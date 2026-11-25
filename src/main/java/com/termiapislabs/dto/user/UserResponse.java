package com.termiapislabs.dto.user;

import com.termiapislabs.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id, String nome, String email, String fotoUrl, String resumo,
                           List<String> links, String pais, String organizacao, String emailContato,
                           boolean emailVerificado, String provedorAuth, Instant dataCriacaoConta) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getNome(), user.getEmail(), user.getFotoUrl(),
            user.getResumo(), List.copyOf(user.getLinks()), user.getPais(), user.getOrganizacao(),
            user.getEmailContato(), user.isEmailVerificado(), user.getProvedorAuth().name(),
            user.getDataCriacaoConta());
    }
}
