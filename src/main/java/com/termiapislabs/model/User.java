package com.termiapislabs.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private Instant dataCriacaoConta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provedorAuth;

    @Column(nullable = false)
    private boolean emailVerificado;

    private String fotoUrl;

    @Column(columnDefinition = "text")
    private String resumo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_links", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "link")
    private List<String> links = new ArrayList<>();

    private String pais;
    private String organizacao;
    private String emailContato;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (dataCriacaoConta == null) dataCriacaoConta = Instant.now();
        if (email != null) email = email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Instant getDataCriacaoConta() { return dataCriacaoConta; }
    public AuthProvider getProvedorAuth() { return provedorAuth; }
    public void setProvedorAuth(AuthProvider provedorAuth) { this.provedorAuth = provedorAuth; }
    public boolean isEmailVerificado() { return emailVerificado; }
    public void setEmailVerificado(boolean emailVerificado) { this.emailVerificado = emailVerificado; }
    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
    public String getResumo() { return resumo; }
    public void setResumo(String resumo) { this.resumo = resumo; }
    public List<String> getLinks() { return links; }
    public void setLinks(List<String> links) { this.links = links == null ? new ArrayList<>() : links; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    public String getOrganizacao() { return organizacao; }
    public void setOrganizacao(String organizacao) { this.organizacao = organizacao; }
    public String getEmailContato() { return emailContato; }
    public void setEmailContato(String emailContato) { this.emailContato = emailContato; }
}
