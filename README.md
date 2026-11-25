# TermiAPILabs — API Backend


<p align="center">
  <img src="docs/assets/TermiApiLabs-logo.jpeg" alt="Logo do TermiAPILabs" width="260">
 </p>

## Visão geral

O TermiAPILabs é uma plataforma open-source para centralizar, documentar e testar APIs REST. O roadmap está organizado em fases:

- **BETA 1:** autenticação e gerenciamento inicial de usuários.
- **BETA 2:** evoluções futuras, incluindo analytics e sandbox.

Este repositório contém o **backend Java Spring Boot**. Ele expõe uma API REST em JSON e concentra validação crítica, persistência, regras de autenticação e segurança.

O frontend é um projeto Flutter Web separado. Ele atua exclusivamente como cliente HTTP, realiza validações de interface e mantém estados de telas, sem acesso direto ao banco de dados ou duplicação das regras críticas do backend. Os arquivos gerados por `flutter build web` podem ser publicados como arquivos estáticos, por exemplo no Firebase Hosting. Não existe Docker para o frontend neste repositório.

## Stack implementada

- Java 17;
- Spring Boot 4.2.0;
- Spring Web MVC;
- Spring Data JPA;
- Bean Validation;
- Spring Security;
- Spring Mail;
- PostgreSQL;
- H2 somente para testes;
- JJWT;
- Google API Client;
- Docker e Docker Compose;
- Maven Wrapper.

## Arquitetura

```text
HTTP / JSON
    |
    v
AuthController
    |
    v
AuthService / EmailCodeService / GoogleAuthService
    |
    v
DTOs + Repositórios JPA
    |
    v
PostgreSQL
```

### Responsabilidades

- **Controllers:** recebem os DTOs validados e exponham os endpoints HTTP.
- **DTOs:** entradas e respostas da API; entidades JPA não são retornadas diretamente.
- **Services:** concentram regras de autenticação e códigos temporários.
- **Repositories:** persistem usuários e códigos.
- **Entities:** modelam os registros JPA.
- **Security:** filtra JWT, aplica CORS e define rotas públicas ou autenticadas.
- **Exception Handler:** normaliza erros de validação, erros de negócio e erros inesperados.

O backend possui uma regra global: todas as rotas iniciadas por `/api/auth/**` são públicas. Demais rotas exigem autenticação, exceto `GET /actuator/health`, caso este endpoint seja disponibilizado pela infraestrutura.

## Estrutura principal

```text
.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── mvnw
├── .env.example
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com/termiapislabs
│   │   │       ├── api
│   │   │       ├── auth
│   │   │       ├── dto
│   │   │       ├── exception
│   │   │       ├── model
│   │   │       ├── repository
│   │   │       └── security
│   │   └── resources
│   │       ├── application.yml
│   │       └── application.properties
│   └── test
│       ├── java
│       └── resources
│           └── application-test.yml
└── docs
    └── CARD-01-Cadastro.md
```

## Fluxo atual do CARD-01

O backend implementa:

- cadastro convencional com confirmação por código;
- reenvio de código com cooldown;
- login convencional bloqueado enquanto o e-mail não estiver verificado;
- login/cadastro com Google por ID Token;
- recuperação de senha por código;
- redefinição de senha;
- logout orientado ao descarte do JWT pelo cliente;
- DTOs de entrada e saída para autenticação e usuário.

Contas Google são criadas com `senhaHash = null`. Portanto, o backend não envia código de recuperação para essas contas. O frontend deve tratar isso apenas como UX, sem inferir a existência ou o provedor da conta por resposta pública. Os detalhes reais estão em `docs/CARD-01-Cadastro.md`.

## Configuração de ambiente

Copie o modelo:

```bash
cp .env.example .env
chmod 600 .env
```

O arquivo `.env` está protegido pelo `.gitignore` e não deve ser versionado.

Variáveis disponíveis:

```dotenv
SERVER_PORT=8080
APP_FRONTEND_URL=http://localhost:5000

DB_URL=jdbc:postgresql://postgres:5432/termiapislabs
DB_USERNAME=termiapislabs_app
DB_PASSWORD=fictitious_database_password

JWT_SECRET=replace_with_at_least_64_random_characters
JWT_EXPIRATION_MINUTES=60

AUTH_CODE_SECRET=replace_with_at_least_32_random_characters
AUTH_CODE_EXPIRATION_MINUTES=15
AUTH_CODE_RESEND_COOLDOWN_SECONDS=60

GOOGLE_CLIENT_ID=fictitious_google_oauth_client_id.apps.googleusercontent.com

SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=fictitious_smtp_username
SMTP_PASSWORD=fictitious_smtp_password
SMTP_FROM=noreply@example.com
```

Gere novos valores secretos localmente antes de executar em produção:

```bash
openssl rand -base64 64
openssl rand -base64 48
openssl rand -base64 24
```

Nunca registre JWT, credenciais SMTP, códigos ou tokens em logs ou commits.

### Banco PostgreSQL

O `docker-compose.yml` inicia:

- PostgreSQL 17 Alpine;
- volume persistente `termiapislabs-postgres`;
- backend na mesma rede Docker;
- porta pública `5432` para PostgreSQL;
- porta pública `8080` para a API.

O backend usa `ddl-auto: update`. Isso é adequado para desenvolvimento inicial, mas não substitui migrações versionadas em produção. Flyway ou Liquibase ainda não foram configurados neste repositório.

### Google

`GOOGLE_CLIENT_ID` deve representar uma credencial OAuth do tipo **Aplicação da Web** e ser a mesma audience usada pelo frontend para o Google Identity Services.

O backend recebe o Google ID Token no campo `credential`. Não deve receber o OAuth `access_token` nem o Client Secret.

`GOOGLE_CLIENT_SECRET` não existe como variável neste backend e não deve ser colocado no frontend.

### SMTP

Para Gmail:

```dotenv
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=seu_email@gmail.com
SMTP_PASSWORD=sua_senha_de_app
SMTP_FROM=seu_email@gmail.com
```

A senha normal da conta não é adequada. Use uma Senha de App gerada em uma conta Google com verificação em duas etapas, desde que o provedor permita esse recurso.

A entrega real depende da configuração SMTP e da política da conta do provedor.

## CORS e integração com Flutter Web

A origem permitida é definida por `APP_FRONTEND_URL`.

Exemplo para desenvolvimento local:

```dotenv
APP_FRONTEND_URL=http://localhost:5000
```

CORS permite:

- origem exatamente igual ao valor configurado;
- métodos `GET`, `POST`, `PUT`, `PATCH`, `DELETE` e `OPTIONS`;
- headers `Authorization`, `Content-Type` e `Accept`;
- credenciais desativadas.

No frontend, use a API por `--dart-define`:

```bash
flutter run -d chrome \
  --web-port=5000 \
  --dart-define=API_BASE_URL=http://localhost:8080 \
  --dart-define=GOOGLE_CLIENT_ID=SEU_CLIENT_ID_PUBLICO.apps.googleusercontent.com
```

Em produção, o frontend pode ser hospedado como arquivos estáticos em Firebase Hosting. Configurar a origem publicada em `APP_FRONTEND_URL` e registrar essa origem no Google OAuth.

## Executar localmente

### Executar tudo com Docker Compose

Na raiz do projeto:

```bash
cp .env.example .env
nano .env
docker compose up -d --build
```

Recriar apenas a API após alteração no backend ou ambiente:

```bash
docker compose up -d --build --force-recreate api
```

Verificar:

```bash
docker compose ps
docker compose logs -f api
docker compose logs -f postgres
```

Testar resposta da API:

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@example.com","senha":"Senha123"}'
```

Parar os serviços:

```bash
docker compose down
```

Para apagar também o volume PostgreSQL:

```bash
docker compose down -v
```

### Executar sem Docker Compose

Pré-requisitos:

- JDK 17;
- PostgreSQL acessível;
- variáveis de ambiente carregadas;
- Maven Wrapper presente.

```bash
set -a
source .env
set +a
./mvnw clean spring-boot:run
```

Executável JAR:

```bash
./mvnw clean package
java -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -jar target/termiapislabs-api-0.0.1-SNAPSHOT.jar
```

## Docker e memória

O `Dockerfile` utiliza build em duas etapas:

1. Eclipse Temurin 17 JDK para compilar;
2. Eclipse Temurin 17 JRE para executar.

A imagem final cria um usuário não administrativo `spring:spring` e permite limitar memória pelo JVM:

```text
-XX:MaxRAMPercentage=75.0
-XX:+UseContainerSupport
-XX:+ExitOnOutOfMemoryError
```

Em ambientes com cerca de 512 MB de RAM, mantenha Hikari, pooling e processos auxiliares enxutos. Ajustes adicionais podem ser necessários sem alterar o código da aplicação.

## Testes

```bash
./mvnw clean test
```

O teste Spring Boot utiliza o perfil `test` e H2 em memória. PostgreSQL não é necessário para o teste de contexto atual.

## Segurança

- `.env` e arquivos de chaves estão ignorados pelo Git;
- o JWT é assinado por chave configurada por ambiente;
- a senha convencional usa `BCryptPasswordEncoder`;
- códigos temporários são armazenados como HMAC-SHA256, não em texto puro;
- entidades JPA não são expostas diretamente nos controllers;
- entradas são validadas com Bean Validation;
- erros são centralizados em `GlobalExceptionHandler`;
- logout é stateless e depende do cliente descartar o JWT;
- não há blacklist, refresh token ou revogação remota implementados;
- a sessão JWT expira conforme `JWT_EXPIRATION_MINUTES`.

Antes de publicar:

- troque todos os secrets já conhecidos ou compartilhados;
- configure SMTP real;
- use PostgreSQL gerenciado ou com credenciais próprias;
- adicione migrações de banco;
- adicione testes de integração;
- revise CORS para os domínios reais;
- não habilite logging HTTP com credenciais em produção.

## Pontos pendentes conhecidos

- Não existe Flyway ou Liquibase configurado.
- Não há endpoint protegido de perfil ou usuário atual.
- Não há endpoint para criar senha em conta Google.
- Não há refresh token.
- Não há blacklist ou revogação imediata de JWT.
- Não há migração segura de contas vinculadas além da atualização existente por e-mail no fluxo Google.
- Não há configuração pública de health check além da autorização descrita no código.
- A entrega SMTP depende da configuração externa e precisa ser monitorada por logs operacionais seguros.
- O logout remoto atual apenas orienta o cliente a descartar o token.

## Contribuição

1. Crie uma branch curta por funcionalidade.
2. Não inclua `.env`, senhas, tokens ou chaves.
3. Mantenha controllers finos e regras em services.
4. Não exponha entidades JPA diretamente.
5. Use DTOs ou records na API.
6. Valide entrada com Bean Validation e `@Valid`.
7. Mantenha mensagens de erro sem vazamento desnecessário.
8. Adicione testes em `src/test/java` e configuração isolada em `src/test/resources`.
9. Execute `./mvnw clean test` antes do pull request.


