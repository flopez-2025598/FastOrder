package com.fareslopez.fastorder.auth.service;

import com.fareslopez.fastorder.auth.dto.AuthResponse;
import com.fareslopez.fastorder.auth.dto.LoginRequest;
import com.fareslopez.fastorder.auth.dto.RegisterRequest;
import com.fareslopez.fastorder.auth.entity.Usuario;
import com.fareslopez.fastorder.auth.enums.Rol;
import com.fareslopez.fastorder.auth.repository.UsuarioRepository;
import com.fareslopez.fastorder.auth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;

    public AuthResponse login(LoginRequest request) {
        // Autentica las credenciales con Spring Security
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Genera y retorna el token
        String jwt = jwtProvider.generateToken(authentication);
        return new AuthResponse(jwt);
    }

    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.findByCorreo(request.getCorreo()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Crear el usuario con la contraseña encriptada (Exigencia de la rúbrica)
        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setCorreo(request.getCorreo());
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(Rol.CLIENTE); // Rol por defecto según rúbrica

        usuarioRepository.save(usuario);

        // Auto-login después del registro para devolver el token
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getCorreo(), request.getPassword())
        );
        String jwt = jwtProvider.generateToken(authentication);

        return new AuthResponse(jwt);
    }
}