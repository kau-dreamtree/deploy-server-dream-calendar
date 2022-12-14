package org.standard.dreamcalendar.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.standard.dreamcalendar.config.*;
import org.standard.dreamcalendar.config.type.TokenType;
import org.standard.dreamcalendar.domain.user.dto.TokenResponse;
import org.standard.dreamcalendar.domain.user.dto.TokenValidationResult;
import org.standard.dreamcalendar.domain.user.dto.UserDto;
import org.standard.dreamcalendar.domain.user.model.*;
import org.standard.dreamcalendar.model.DtoConverter;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

import static org.standard.dreamcalendar.config.type.TokenValidationType.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final Encryptor encryptor;
    private final JwtTokenProvider tokenProvider;
    private final DtoConverter converter;

    @Transactional(rollbackFor = Exception.class)
    public Boolean create(UserDto userDto) {

        try {
            userDto.setRole(Role.USER);
            userDto.setPassword(encryptor.SHA256(userDto.getPassword()));
            userRepository.save(converter.toUserEntity(userDto));
            return true;

        } catch (NoSuchAlgorithmException e) {

            log.error("UserService create()={}", e);
            return false;

        }

    }

    @Transactional
    public TokenResponse logInByEmailPassword(UserDto userDto)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

        // Check email address in DB
        User user = userRepository.findByEmail(userDto.getEmail()).orElse(null);

        // Check password in DB
        String givenPassword = encryptor.SHA256(userDto.getPassword());

        if (user == null || !givenPassword.equals(user.getPassword()))
            return null;

        // Save & issue tokens
        String accessToken = tokenProvider.generate(user.getEmail(), TokenType.AccessToken);
        String refreshToken = tokenProvider.generate(user.getEmail(), TokenType.RefreshToken);

        user.updateAccessToken(accessToken);
        user.updateRefreshToken(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * 1. 입력된 토큰이 유효하여 ID를 추출할 수 있음 <br>
     * 2. 해당 ID의 사용자가 DB에 존재함 <br>
     * 3. 해당 ID의 accessToken과 입력된 토큰이 일치함 <br>
     * <p> 위 세 조건을 모두 만족하면 return true
     * <p> 1. 토큰이  만료된 경우 401 Unauthorized <br>
     * 2. 토큰이 DB에 없거나 다를 겅우 400 Bad Request
     */
    @Transactional
    public HttpStatus logInByAccessToken(String accessToken)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        User user = userRepository.findByAccessToken(accessToken).orElse(null);

        TokenValidationResult validation = tokenProvider.validateToken(accessToken, TokenType.AccessToken);

        if (user == null || validation.getType() == INVALID)
            return HttpStatus.BAD_REQUEST;

        if (validation.getType() == EXPIRED)
            return HttpStatus.UNAUTHORIZED;

        return HttpStatus.ACCEPTED;
    }

    /**
     * 1. Refresh token이 서버와 일치하는지 확인 <br>
     * 2. 정상이고 만료되지 않은 경우 access token만 갱신하여 return <br>
     * 3. 정상이고 곧 만료되는 경우 두 토큰 모두 갱신하여 return <br>
     * 4. DB에 없거나 만료된 경우 return null, 400 BAD_REQUEST로 클라이언트에서 로그아웃 <br>
     *
     *
     * @param refreshToken
     * @return
     */
    @Transactional
    public TokenResponse updateAccessToken(String refreshToken)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        User user = userRepository.findByRefreshToken(refreshToken).orElse(null);

        TokenValidationResult validation = tokenProvider.validateToken(refreshToken, TokenType.RefreshToken);

        if (user == null || validation.getType() == EXPIRED || validation.getType() == INVALID)
            return null;

        if (validation.getType() == UPDATE) {
            refreshToken = tokenProvider.generate(user.getEmail(), TokenType.RefreshToken);
            userRepository.updateRefreshToken(user.getId(), refreshToken);
        }

        String accessToken = tokenProvider.generate(user.getEmail(), TokenType.AccessToken);
        userRepository.updateAccessToken(user.getId(), accessToken);


        return TokenResponse.builder()
                .accessToken(user.getAccessToken())
                .refreshToken(user.getRefreshToken())
                .build();

    }
//
//    @Transactional
//    public String updateRefreshToken(String refreshToken) {
//
//    }

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        List<User> userList = userRepository.findAll();
        return userList.stream().map(converter::toUserDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto findById(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        return converter.toUserDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> findUsersByUsername(String username) {
        List<User> userList = userRepository.findByName(username);
        return userList.stream().map(converter::toUserDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto findByEmail(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return converter.toUserDto(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(Integer id) {

        User user = userRepository.findById(id).orElse(null);

        if (user == null)
            return false;

        userRepository.deleteById(id);
        return true;

    }

}