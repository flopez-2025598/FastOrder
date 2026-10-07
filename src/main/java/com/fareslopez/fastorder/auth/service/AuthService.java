package com.fareslopez.fastorder.auth.service;

import com.fareslopez.fastorder.auth.dto.AuthResponse;
import com.fareslopez.fastorder.auth.dto.LoginRequest;
import com.fareslopez.fastorder.auth.dto.RegisterRequest;
import com.fareslopez.fastorder.auth.entity.Usuario;
import com.fareslopez.fastorder.auth.enums.Rol;
import com.fareslopez.fastorder.auth.repository.UsuarioRepository;
import com.fareslopez.fastorder.auth.security.JwtProvider;
import com.fareslopez.fastorder.common.exception.DuplicateResourceException;
import com.fareslopez.fastorder.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizarEmail(request.email());

        // Lanza BadCredentialsException (-> 401) si el email o la contraseña no coinciden
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return buildResponse(usuario);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("El email '" + email + "' ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setDireccion(request.direccion());
        usuario.setTelefono(request.telefono());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(Rol.CLIENTE); // Rol predeterminado: nunca se acepta desde el cliente

        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultáneos con el mismo email
            throw new DuplicateResourceException("El email '" + email + "' ya está registrado");
        }
        return buildResponse(usuario);
    }

    private AuthResponse buildResponse(Usuario usuario) {
        String token = jwtProvider.generateToken(usuario.getEmail(), usuario.getRol().name());
        return AuthResponse.bearer(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
