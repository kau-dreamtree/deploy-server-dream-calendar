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
     * 1. ????????? ????????? ???????????? ID??? ????????? ??? ?????? <br>
     * 2. ?????? ID??? ???????????? DB??? ????????? <br>
     * 3. ?????? ID??? accessToken??? ????????? ????????? ????????? <br>
     * <p> ??? ??? ????????? ?????? ???????????? return true
     * <p> 1. ?????????  ????????? ?????? 401 Unauthorized <br>
     * 2. ????????? DB??? ????????? ?????? ?????? 400 Bad Request
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
     * 1. Refresh token??? ????????? ??????????????? ?????? <br>
     * 2. ???????????? ???????????? ?????? ?????? access token??? ???????????? return <br>
     * 3. ???????????? ??? ???????????? ?????? ??? ?????? ?????? ???????????? return <br>
     * 4. DB??? ????????? ????????? ?????? return null, 400 BAD_REQUEST??? ????????????????????? ???????????? <br>
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