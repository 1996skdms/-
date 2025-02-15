package com.metamong.server.controller;


import com.metamong.server.config.RedisUtil;
import com.metamong.server.dto.EmailDto;
import com.metamong.server.dto.UserDto;
import com.metamong.server.entity.FirebaseToken;
import com.metamong.server.entity.User;
import com.metamong.server.exception.ApplicationException;
import com.metamong.server.repository.FirebaseTokenRepository;
import com.metamong.server.repository.UserRepository;
import com.metamong.server.service.EmailSenderService;
import com.metamong.server.service.FirebaseCloudMessageService;
import com.metamong.server.service.JwtService;
import com.metamong.server.service.UserService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private FirebaseTokenRepository firebaseTokenRepository;

    @Autowired
    private FirebaseCloudMessageService firebaseCloudMessageService;

    @Value("${token.accesstoken}")
    private String accessToken;

    @Value("${token.refreshtoken}")
    private String refreshToken;

    /***
     *
     * @param registerInfo : 회원가입 정보
     * @return
     * @throws IOException
     */
    @PostMapping("")
    @ApiOperation(value="회원가입", notes = "사용자가 회원가입을 시도한다.")
    public ResponseEntity register(
            @RequestBody  @ApiParam(value="회원가입 정보")
            UserDto.RegisterRequest registerInfo, BindingResult bindingResult
            ) throws IOException {

        if (bindingResult.hasErrors())
            return ResponseEntity.status(400).build();

        if (userService.isExistEmail(registerInfo.getEmail()))
            return ResponseEntity.status(409).build();

        userService.validatePassword(registerInfo.getPassword());

        Map<String, Integer> map = new HashMap<>();
        map.put("id", userService.register(registerInfo));
        return ResponseEntity.status(201).body(map);
    }

    /***
     *
     * @param updateInfo : 회원수정 정보
     * @return
     * @throws IOException
     */
    @PutMapping("/my-info")
    @ApiOperation(value="회원정보 수정", notes = "사용자가 자신의 정보를 수정한다.")
    public ResponseEntity update(
            @RequestBody @ApiParam(value="회원수정 정보", required = true) UserDto.UpdateRequest updateInfo, HttpServletRequest request
        ) throws IOException{
    	
        if(updateInfo.getOriginPassword() == null || updateInfo.getOriginPassword().equals("")) return ResponseEntity.status(401).build();
        userService.checkPassword(updateInfo, request);

        if(updateInfo.getNickname() != null && !updateInfo.getNickname().equals("")){
            userService.updateNickname(updateInfo, request);
        }
        if(updateInfo.getNewPassword() != null && !updateInfo.getNewPassword().equals("")){
            userService.validatePassword(updateInfo.getNewPassword());
            userService.updatePassword(updateInfo, request);
        }
        if(updateInfo.getNewPassword() == null && updateInfo.getNickname() == null)
            throw new ApplicationException(HttpStatus.valueOf(401), "수정할 정보가 없습니다.");

        return ResponseEntity.status(200).build();
    }
    
    /***
    *
    * @param updateInfo : 회원수정 정보
    * @return
    * @throws IOException
    */
   @PutMapping("/my-info/{nickname}")
   @ApiOperation(value="회원정보 닉네임만 수정", notes = "카카오 로그인 시 닉네임만 수정한다.")
   public ResponseEntity updateNickname(@PathVariable String nickname, HttpServletRequest request
       ) throws IOException{

       if(nickname != null && !nickname.equals("")){
    	   Optional<User> user = userRepository.findById((int) request.getAttribute("userId"));
           if(!user.isPresent()) throw new ApplicationException(HttpStatus.valueOf(401), "회원 정보가 없습니다.");

           user.ifPresent(userSelect -> {
               userSelect.setNickname(nickname);
               userRepository.save(userSelect);
           });
       }

       if(nickname == null)
           throw new ApplicationException(HttpStatus.valueOf(401), "수정할 정보가 없습니다.");

       return ResponseEntity.status(200).build();
   }

    /***
     *
     * @return
     * @throws IOException
     */
    @DeleteMapping("")
    @ApiOperation(value="회원탈퇴", notes = "사용자가 자신의 정보를 삭제하고 탈퇴한다.")
    public ResponseEntity delete(HttpServletRequest request) throws IOException{
        int userId = (int) request.getAttribute("userId");
        User user = userRepository.findById(userId).orElse(null);
        if(user != null) {
            userRepository.delete(user);
            return ResponseEntity.status(200).build();
        }else{
            throw new ApplicationException(HttpStatus.valueOf(401), "삭제할 정보가 없습니다.");
        }
    }

    /***
     *
     * @param loginInfo : 로그인 정보
     * @return
     * @throws IOException
     */
    @PostMapping("login")
    @ApiOperation(value="로그인", response =UserDto.Response.class)
    public ResponseEntity login(
            @RequestBody  @ApiParam(value="로그인 정보", required = true) UserDto.LoginRequest loginInfo
            ) throws InterruptedException, IOException{
        UserDto.LoginRes loginRes = userService.login(loginInfo);
        Map<String, Object> map = jwtService.createToken(loginRes.getId());
        System.out.println("map : "+ map.get(accessToken));

        firebaseCloudMessageService.save(loginRes, loginInfo.getFirebaseToken());

        HttpHeaders resHeader = new HttpHeaders();

        resHeader.set(accessToken, (String) map.get(accessToken));
        System.out.println("resHeader : "+ resHeader.get(accessToken));
        resHeader.set(refreshToken, (String) map.get(refreshToken));
        System.out.println("resHeader : "+ resHeader.get(refreshToken));

        
        
        return ResponseEntity.ok().headers(resHeader).body(loginRes);
    }

    /***
     *
     * @param type : 닉네임 or 이메일
     * @param data : 닉네임이나 이메일 정보
     * @return
     * @throws IOException
     */
    @GetMapping("duplicate")
    @ApiOperation(value="중복확인", notes="이메일/닉네임 중복확인")
    public ResponseEntity duplicate(
            @RequestParam @ApiParam(value="닉네임 or 이메일") String type, @RequestParam @ApiParam(value="닉네임이나 이메일 정보") String data
            , HttpServletRequest request) throws IOException{
        // type Email 중복검사
        System.out.println("중복검사 시작합니다... "+ type +" / "+ data);
        if (type.equals("email")){
            if (userService.isExistEmail(data)) return new ResponseEntity(HttpStatus.valueOf(400));
            return new ResponseEntity(HttpStatus.valueOf(200));
       // type Nickname 중복검사
        }else if(type.equals("nickname")){
            if (userService.isExistNickname(data)) return new ResponseEntity(HttpStatus.valueOf(400));
            return new ResponseEntity(HttpStatus.valueOf(200));
        }else 
            System.out.println("수정할 정보를 입력하세요");
            return ResponseEntity.status(400).build();
    }

    /***
     *
     * @return
     * @throws IOException
     */
    @PostMapping("email")
    @ApiOperation(value="이메일 인증")
    public ResponseEntity checkEmail(
            @RequestBody @ApiParam(value="이메일", required = true) UserDto.EmailRequest emailReq
            ) throws IOException{
        System.out.println("----이메일 인증-----");
        EmailDto emailDto = new EmailDto();
        emailDto.setCode(emailSenderService.createKey());
        emailDto.setToEmail(emailReq.getEmail());
        emailDto.setTitle("메타몽 서비스 인증메일입니다.");
        emailDto.setValidTime(30 * 60 * 1000L);

        emailSenderService.sendEmail(emailDto);
        System.out.println("----이메일 인증 보냄----");
        return ResponseEntity.status(200).build();
    }

    @PostMapping("email-check")
    @ApiOperation(value="이메일 인증 확인")
    public boolean checkEmailConfirm(
            @RequestBody @ApiParam(value="이메일 인증 확인", required = true) UserDto.EmailResponse emailRes
    ) throws IOException{
        System.out.println(emailRes.getCode());


        String email = redisUtil.getData(emailRes.getCode());
        if (email == null) { // email이 존재하지 않으면, 유효 기간 만료이거나 코드 잘못 입력
            throw new ApplicationException(HttpStatus.valueOf(401), "이메일 인증이 만료되었거나, 코드가 맞지 앉습니다.");
        }
        System.out.println(email);
        System.out.println(emailRes.getEmail());
        return email.equals(emailRes.getEmail());
    }

    @PostMapping("/find-pw")
    @ApiOperation(value = "이메일로 임시 비밀번호 전송")
    public ResponseEntity sendTempPw(@RequestBody EmailDto emailInfo){
        if(userService.isExistEmail(emailInfo.getToEmail())){       // 이메일 검증

            String tmpPassword = UUID.randomUUID().toString().split("-")[0];
            EmailDto emailDto = EmailDto.builder()
            .toEmail(emailInfo.getToEmail())
                    .title("메타몽 서비스 : 임시 비밀번호")
                    .code(tmpPassword)
                    .build();

            userService.resetPassword(tmpPassword, emailInfo.getToEmail());
            emailSenderService.sendEmailFindPw(emailDto);
        }

        return ResponseEntity.ok().build();
    }

    /***
     *
     * @param firebaseToken : 로그인 토큰
     * @return
     * @throws IOException
     */
    @Transactional
    @DeleteMapping("login")
    @ApiOperation(value="로그아웃")
    public ResponseEntity logout(
            @RequestParam @ApiParam(value="Token") String firebaseToken,@RequestParam @ApiParam String userId
            ) throws IOException{
        
        // DB 파이어베이스 토큰 삭제하기
        firebaseCloudMessageService.del(firebaseToken);
        
        // 오프라인 변경
        Optional<List<FirebaseToken>> low_token = firebaseTokenRepository.findByUserId(Integer.parseInt(userId));
        System.out.println(low_token.get().size());
        if(low_token.get().size()==0) {
	        User user = userRepository.findById(Integer.parseInt(userId)).get();
	        user.setState(0);
	        userRepository.save(user);
        }
        return ResponseEntity.status(200).build();
        
    }

    /***
     *
     * @return
     * @throws IOException
     */
    @PostMapping("login-kakao")
    @ApiOperation(value="카카오톡 로그인")
    public ResponseEntity<UserDto.Response> loginkakao(@RequestBody Map<String, String> payload) throws IOException{

        String email = payload.get("email");
        String name = payload.get("name");
        
        
        User user = new User();
        if(!userService.isExistEmail(email)) {
            // 이메일 없으면 최초 로그인이므로 회원 정보 DB에 등록
            userService.kakaoRegister(email, name);
        }

        // 이메일 이미있으면 가입된 유저이므로 유저 정보 가져와서 넘겨줌
        UserDto.Response res = userService.login(email);
        firebaseCloudMessageService.save(res, payload.get("firebaseToken"));
        
        // 온라인
        User ouser = userRepository.findByEmail(email).get();
        ouser.setState(1);
        userRepository.save(ouser);

        Map<String, Object> map = jwtService.createToken(res.getId());
        System.out.println("map : "+ map.get(accessToken));
        HttpHeaders resHeader = new HttpHeaders();

        resHeader.set(accessToken, (String) map.get(accessToken));
        System.out.println("resHeader : "+ resHeader.get(accessToken));
        resHeader.set(refreshToken, (String) map.get(refreshToken));
        System.out.println("resHeader : "+ resHeader.get(refreshToken));

        return ResponseEntity.ok().headers(resHeader).body(res);
    }

    @GetMapping("/my-info")
    @ApiOperation(value = "본인 정보 가져오기")
    public ResponseEntity getMyInfo(HttpServletRequest request) throws IOException {
        int userId = (int) request.getAttribute("userId");

        UserDto.userInfoResponse res = userService.getMyInfo(userId);

        return ResponseEntity.ok().body(res);
    }

    /***
     *
     * @param nickname : 다른 사용자의 닉네임
     * @return
     * @throws IOException
     */
    @GetMapping("{nickname}")
    @ApiOperation(value="다른 사용자의 정보 조회")
    public ResponseEntity<UserDto.userInfoResponse> userinfo( @PathVariable String nickname) throws IOException {

        UserDto.userInfoResponse res = userService.getUserInfo(nickname);

        return ResponseEntity.ok().body(res);
    }

    /***
    *
    * @param updateInfo : 처음 왔을때
    * @return
    * @throws IOException
    */
   @PutMapping("/tutorial")
   @ApiOperation(value="tutorial 수정", notes = "처음 로그인 여부 확인")
   public ResponseEntity updateTutorial(HttpServletRequest request) throws IOException{

	   int userId = (Integer) request.getAttribute("userId");
       User user = userRepository.findById(userId).get();
       user.setTutorial(1);
       userRepository.save(user);
       return ResponseEntity.status(200).build();
   }
}

