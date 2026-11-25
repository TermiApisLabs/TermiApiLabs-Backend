# CARD-01 — Cadastro, autenticação e recuperação de conta

## Estado da implementação

Este documento descreve exclusivamente o backend encontrado neste workspace para `[BETA-1][CARD-01]`.

O backend implementa cadastro convencional, confirmação por código, reenvio de código, login, Google, recuperação de senha, redefinição e logout orientado ao cliente.

Não foram encontrados neste repositório:

- endpoint de usuário atual;
- endpoint de edição de perfil;
- refresh token;
- blacklist ou revogação imediata de JWT;
- endpoint para criar senha em conta Google;
- migrações Flyway/Liquibase;
- rate limiting distribuído além do cooldown ligado ao registro de código;
- testes abrangendo todos os fluxos do card.

## Contexto de integração

O backend expõe uma API REST em JSON consumida por um frontend Flutter Web separado.

A origem permitida para o navegador é configurada por `APP_FRONTEND_URL`. O arquivo de exemplo usa `http://localhost:5000`; o backend permite exatamente essa origem ou outro valor definido no ambiente.

O frontend recebe a URL da API por configuração pública no momento do build, por exemplo:

```bash
flutter run -d chrome \
  --web-port=5000 \
  --dart-define=API_BASE_URL=http://localhost:8080
```

Não é necessário Docker no frontend.

## Camadas implementadas

### Controller

`AuthController` está em `com.termiapislabs.auth` e expõe:

```text
/api/auth
```

O controller recebe DTOs validados e delega as regras para `AuthService`.

### Services

- `AuthService`: cadastro, login, confirmação, Google, recuperação e redefinição.
- `EmailCodeService`: geração segura de códigos, cooldown, expiração, consumo e invalidação.
- `EmailService`: envio SMTP por Spring Mail.
- `GoogleAuthService`: validação do Google ID Token.
- `JwtService`: geração e validação JWT.

### Repositórios

- `UserRepository`: busca e existência por e-mail e persistência de usuário.
- `AuthCodeRepository`: busca códigos ativos e último código emitido por usuário e tipo.

### Entidades

`User` contém:

- `nome`;
- `email` normalizado em minúsculas por `@PrePersist`;
- `senhaHash`;
- `dataCriacaoConta`;
- `provedorAuth`;
- `emailVerificado`;
- `fotoUrl`;
- `resumo`;
- `links`;
- `pais`;
- `organizacao`;
- `emailContato`.

`AuthCode` contém:

- `userId`;
- `codeHash`;
- `type`;
- `createdAt`;
- `expiresAt`;
- `usedAt`;
- `invalidatedAt`.

Os tipos são:

```java
enum AuthCodeType {
    EMAIL_CONFIRMATION,
    PASSWORD_RESET
}
```

Os provedores são:

```java
enum AuthProvider {
    LOCAL,
    GOOGLE
}
```

## Segurança

### Senhas

O cadastro convencional usa `BCryptPasswordEncoder` para o `senhaHash`.

A política implementada exige:

- mínimo 8 caracteres;
- máximo 72 caracteres;
- pelo menos uma letra maiúscula;
- pelo menos uma letra minúscula;
- pelo menos um número.

O endpoint retorna apenas `UserResponse` ou `AuthResponse`.

### Códigos temporários

Os códigos são números aleatórios de seis dígitos.

No banco é armazenado um HMAC-SHA256 do código usando `AUTH_CODE_SECRET`. O código puro não aparece em `AuthCode` nem nas respostas dos controllers.

Um código é invalidado:

- após uso;
- ao emitir outro código para o mesmo usuário e tipo;
- após expirar;

A expiração usa `AUTH_CODE_EXPIRATION_MINUTES`, cujo padrão é 15 minutos.

### JWT

O JWT contém:

- subject como UUID do usuário;
- claim `email`;
- claim `name`;
- data de emissão;
- data de expiração.

A expiração usa `JWT_EXPIRATION_MINUTES`, cujo padrão é 60 minutos.

O filtro procura o token no header:

```http
Authorization: Bearer <token>
```

Todas as rotas `/api/auth/**` são públicas na configuração atual.

Não existe blacklist ou refresh token implementado. Portanto, logout depende do cliente descartar o JWT.

### CORS

`SecurityConfig` permite:

- origem única: `APP_FRONTEND_URL`;
- `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`;
- `Authorization`, `Content-Type`, `Accept`;
- credenciais CORS desativadas.

## Contratos e endpoints

Todos os endpoints abaixo são públicos pela regra `/api/auth/**`.

### 1. Cadastro convencional

```http
POST /api/auth/register
Content-Type: application/json
```

Request:

```json
{
  "nome": "Nome Completo",
  "email": "usuario@example.com",
  "senha": "Senha123",
  "confirmarSenha": "Senha123"
}
```

Validações Bean Validation:

- `nome`: obrigatório, máximo 100 caracteres;
- `email`: obrigatório, formato válido, máximo 255 caracteres;
- `senha`: obrigatório, mínimo 8 e máximo 72 caracteres;
- `confirmarSenha`: obrigatório, mínimo 8 e máximo 72 caracteres.

Validações adicionais:

- senha igual à confirmação;
- política de senha;
- e-mail normalizado em minúsculas;
- conflito se o e-mail já existir.

Resposta:

- `201 Created`;
- body `UserResponse`;
- `emailVerificado=false`;
- `provedorAuth=LOCAL`;
- não retorna JWT no controller.

Erros observados:

- `400 Bad Request`: confirmação divergente ou senha inválida;
- `409 Conflict`: e-mail já existente;
- `400 Bad Request`: payload ou e-mail inválido;
- `500 Internal Server Error`: falha inesperada.

O usuário recebe um código de confirmação por e-mail.

### 2. Confirmação de e-mail

```http
POST /api/auth/confirm-email
Content-Type: application/json
```

Request:

```json
{
  "email": "usuario@example.com",
  "codigo": "123456"
}
```

Validações:

- `email`: obrigatório e formato válido;
- `codigo`: obrigatório e exatamente seis dígitos.

Resposta:

- `200 OK`;
- `UserResponse`;
- `emailVerificado=true` após sucesso.

Erros:

- `400 Bad Request`: código inválido, expirado ou e-mail inexistente;
- `400 Bad Request`: validação de payload.

O código usado é marcado como usado e invalidado.

### 3. Reenvio de código

```http
POST /api/auth/resend-code
Content-Type: application/json
```

Request:

```json
{
  "email": "usuario@example.com"
}
```

Validações:

- `email`: obrigatório e formato válido.

Resposta:

```json
{
  "message": "Se a conta existir e estiver pendente, um novo código será enviado."
}
```

Regras:

- conta inexistente não é revelada;
- conta já verificada não recebe código;
- conta não verificada pode receber novo código;
- o código anterior é invalidado;
- cooldown usa `AUTH_CODE_RESEND_COOLDOWN_SECONDS`;
- padrão configurado no `.env.example`: 60 segundos.

Erros:

- `200 OK`: resposta genérica;
- `429 Too Many Requests`: cooldown ativo;
- `400 Bad Request`: e-mail inválido.

### 4. Login convencional

```http
POST /api/auth/login
Content-Type: application/json
```

Request:

```json
{
  "email": "usuario@example.com",
  "senha": "Senha123"
}
```

Validações:

- `email`: obrigatório e formato válido;
- `senha`: obrigatória.

Resposta de sucesso:

- `200 OK`;
- `AuthResponse`.

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "user": {
    "id": "uuid",
    "nome": "Nome",
    "email": "usuario@example.com",
    "fotoUrl": null,
    "resumo": null,
    "links": [],
    "pais": null,
    "organizacao": null,
    "emailContato": null,
    "emailVerificado": true,
    "provedorAuth": "LOCAL",
    "dataCriacaoConta": "2026-11-24T12:00:00Z"
  }
}
```

Erros:

- `401 Unauthorized`: e-mail ou senha inválidos;
- `403 Forbidden`: conta local não confirmada;
- `400 Bad Request`: payload inválido;
- `500 Internal Server Error`: falha inesperada.

Mensagem real de `403`:

```text
Confirme seu e-mail antes de entrar. Você pode solicitar o reenvio do código.
```

A conta com `provedorAuth=GOOGLE` ou `senhaHash=null` não faz login por senha.

### 5. Login ou cadastro Google

```http
POST /api/auth/google
Content-Type: application/json
```

Request:

```json
{
  "credential": "google_id_token"
}
```

O valor é o Google ID Token. Não deve ser substituído por um OAuth `access_token`.

Comportamento:

- o backend valida o ID Token com `GOOGLE_CLIENT_ID` como audience;
- exige `email_verified=true`;
- usa `email`, `name` e `picture` do payload;
- cria conta Google quando o e-mail não existir;
- define `senhaHash=null`;
- define `emailVerificado=true`;
- atualiza `emailVerificado=true` em conta existente;
- atualiza nome somente se estiver vazio;
- atualiza `fotoUrl` se houver `picture`;
- não altera explicitamente o `provedorAuth` de uma conta existente.

Resposta:

- `200 OK`;
- `AuthResponse` com JWT.

Erros:

- `400 Bad Request`: credential ausente;
- `401 Unauthorized`: credential inválida;
- `401 Unauthorized`: e-mail não confirmado pelo Google;
- `401 Unauthorized`: falha ao validar credential.

### 6. Solicitação de recuperação de senha

```http
POST /api/auth/forgot-password
Content-Type: application/json
```

Request:

```json
{
  "email": "usuario@example.com"
}
```

Resposta:

- `200 OK`;
- resposta genérica independente da existência da conta.

```json
{
  "message": "Se o e-mail estiver cadastrado, você receberá as instruções."
}
```

Comportamento real:

- conta Google não recebe código, pois `senhaHash=null`;
- conta local com `senhaHash != null` pode receber código;
- conta inexistente não é revelada;
- cooldown usa o mesmo intervalo de códigos temporários;
- o frontend não deve interpretar `200` como confirmação de envio.

Erro válido:

- `400 Bad Request`: e-mail inválido;
- `429 Too Many Requests`: cooldown ativo;
- `500 Internal Server Error`: falha inesperada.

### 7. Redefinição de senha

```http
POST /api/auth/reset-password
Content-Type: application/json
```

Request:

```json
{
  "email": "usuario@example.com",
  "codigo": "123456",
  "novaSenha": "NovaSenha123",
  "confirmarNovaSenha": "NovaSenha123"
}
```

Validações:

- `email`: obrigatório e formato válido;
- `codigo`: obrigatório e exatamente seis dígitos;
- `novaSenha` e `confirmarNovaSenha`: mín. 8, máx. 72;
- senhas devem ser iguais;
- senha deve cumprir maiúscula, minúscula e número.

Resposta:

- `200 OK`;
- `AuthResponse` com novo JWT;
- senha substituída pelo BCrypt;
- código marcado como usado e inválido.

Erros:

- `400 Bad Request`: senhas divergentes;
- `400 Bad Request`: política inválida;
- `400 Bad Request`: código inválido ou expirado;
- `500 Internal Server Error`.

Não existe no backend atual um fluxo onde conta Google possa criar uma senha por meio desta rota.

### 8. Logout

```http
POST /api/auth/logout
```

Não possui request body obrigatório.

Resposta:

```json
{
  "message": "Logout realizado. Descarte o token no cliente."
}
```

Status:

- `200 OK`.

Esse endpoint não invalida o JWT. O frontend deve remover token e dados locais da sessão.

## Formato global de erros

Os `ApiException` são transformados em:

```json
{
  "timestamp": "2026-11-24T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados inválidos.",
  "fieldErrors": {}
}
```

Validações de Bean Validation retornam `400` e um mapa `fieldErrors` associado aos campos.

O frontend deve preferir `message`, usar `fieldErrors` quando disponível e manter logs sanitizados.

## Resposta de usuário

```json
{
  "id": "uuid",
  "nome": "Nome",
  "email": "usuario@example.com",
  "fotoUrl": null,
  "resumo": null,
  "links": [],
  "pais": null,
  "organizacao": null,
  "emailContato": null,
  "emailVerificado": false,
  "provedorAuth": "LOCAL",
  "dataCriacaoConta": "2026-11-24T12:00:00Z"
}
```

Nenhum campo `senhaHash` ou `codeHash` é incluído.

## Integração com Flutter Web

O frontend deve isolar chamadas HTTP em um serviço ou repositório de autenticação.

Recomendações:

- configurar URL por `--dart-define=API_BASE_URL`;
- configurar Google Client ID público por `--dart-define`;
- usar headers JSON;
- implementar loading e cold start;
- não registrar senha, token ou código;
- não enviar o Google `access_token` como credential;
- enviar o Google ID Token no campo `credential`;
- remover JWT e usuário no logout;
- não afirmar que recuperação enviou código após `200`;
- informar que contas Google usam login Google no usuário já autenticado;
- não tentar descobrir provedor pelo e-mail público.

## Pontos pendentes e atenção manual

### Backend

- `ddl-auto: update` não é uma migração versionada.
- não existe endpoint protegido de perfil.
- não existe endpoint para usuário atual.
- não existe criação posterior de senha em conta Google.
- não existe refresh token.
- não existe revogação imediata de JWT.
- não existe rate limiting global ou distribuído além do cooldown por usuário/tipo.
- falha SMTP pode exigir tratamento adicional para separar erro operacional da resposta pública.
- contas locais que também passam pelo Google podem manter `provedorAuth=LOCAL` ou o provedor atual definido no registro, pois o backend não altera explicitamente o provedor na lógica existente.

### Configuração

- confirmar o valor de `APP_FRONTEND_URL` em cada ambiente;
- confirmar o Google Client ID web real;
- confirmar o mesmo Google Client ID no backend e frontend;
- confirmar SMTP e sender autorizado;
- confirmar credenciais não expostas;
- substituir secrets reais antes de publicação;
- configurar CORS com o domínio Firebase em produção.

### Fluxo para contas Google

Conta Google não recebe código de redefinição por `senhaHash=null`.

Isso está implementado no backend e não é obrigatório criar uma senha nesta primeira versão. Se o produto decidir permitir senha local em conta Google, será necessário um endpoint autenticado separado e um campo seguro indicando `possuiSenha` sem expor o hash.

## Checklist de validação

```bash
./mvnw clean test
```

```bash
docker compose up -d --build
docker compose logs -f api
```

Testes manuais recomendados:

- cadastro convencional retorna `201`;
- confirmação altera `emailVerificado=true`;
- login bloqueia conta não confirmada com `403`;
- reenvio respeita cooldown;
- Google real usa ID Token iniciado por `eyJ` e retorna `200`;
- conta Google não recebe código de recuperação;
- conta local envia código via SMTP;
- redefinição atualiza senha por BCrypt;
- logout remove token no frontend;
- CORS aceita somente a origem configurada.
