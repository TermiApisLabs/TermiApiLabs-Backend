package com.termiapislabs.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.termiapislabs.exception.ApiException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthService {
    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${app.google-client-id}") String clientId) {
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(clientId))
            .build();
    }

    public GoogleIdToken.Payload verify(String credential) {
        try {
            GoogleIdToken idToken = verifier.verify(credential);
            if (idToken == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Credencial do Google inválida.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "O e-mail do Google não foi confirmado.");
            }

            if (payload.getEmail() == null || payload.getEmail().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "A credencial do Google não informou um e-mail.");
            }

            return payload;
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                "Não foi possível validar a credencial do Google.");
        }
    }
}
