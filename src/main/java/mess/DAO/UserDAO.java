package mess.DAO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mess.answers.Answer;
import mess.answers.AnswerGetToken;
import mess.answers.PayloadAccess;
import mess.entities.User;
import mess.repositories.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class UserDAO {
    private Environment env;
    private UserRepository userRepository;

    public UserDAO(Environment env, UserRepository userRepository) {
        this.env = env;
        this.userRepository = userRepository;
    }

    //Кодирование Base64
    private String baseEncoding(String string){
        return Base64.getEncoder().encodeToString(string.getBytes());
    }

    //Декодирование Base64
    private String baseDecode(String string){
        return new String(Base64.getDecoder().decode(string));
    }
    //создание подписи
    private String generateHash(String string) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        //получение секретного ключа с файла пропертис
        String key = env.getProperty("message.usertable.password");
        //установка алгоритма шифрования
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(new SecretKeySpec(key.getBytes(), "HmacSHA256"));
        //шифрование
        byte[] bytes = sha256_HMAC.doFinal(string.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        //конвертирование в строку
        for (final byte element : bytes)
        {
            result.append(Integer.toString((element & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    //проверка пароля и возврат токенов
    public String gettingToken(String username, String password) throws JsonProcessingException {
        //поиск юзера по username
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if(optionalUser.isPresent()){
            //если такой username существует и пароль верный
            if(optionalUser.get().getPassword().equals(password)){
                //создание успешного ответа с токенами
                AnswerGetToken answerGetToken = new AnswerGetToken();
                answerGetToken.error = false;
                answerGetToken.message = "success";
                answerGetToken.user = optionalUser.get();
                return new ObjectMapper().writeValueAsString(answerGetToken);
            }
        }
        //создание ошибочного ответа
        Answer answer = new Answer();
        answer.error = true;
        answer.message = "Неправильно введен username или password";
        return new ObjectMapper().writeValueAsString(answer);
    }

    //генерация рефреш токена
    public String generateRefreshToken() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        return uuid1.concat(uuid2).concat(uuid3).replace("-","");
    }
    //геренация аксес токена
    public String generateAccessToken(User user) throws JsonProcessingException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        //создание заголовка токена(он всегда один)
        String header = baseEncoding("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        //создание полезные данные
        PayloadAccess payloadAccess = new PayloadAccess();
        //устанавливаем username
        payloadAccess.setUsername(user.getUsername());
        //устанавливаем время жизни токена
        payloadAccess.setExp(String.valueOf(new Date().getTime() + 600000));
        String payout = baseEncoding(new ObjectMapper().writeValueAsString(payloadAccess));
        //конкатенируем заголовок и полезные данные
        String concatHeaderPayout = header.concat(".").concat(payout);
        //создаем подпись. Строка с заголовком и полезными данными кодируется и переводится в Base64
        String signature = baseEncoding(generateHash(concatHeaderPayout));
        //отправка токена
        return concatHeaderPayout.concat(".").concat(signature);
    }

    //проверка рефреш токена и генерация новых токенов
    public String generateTokens(String refresh) throws JsonProcessingException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        Optional<User> optionalUser = userRepository.findByRefresh(refresh);
        //если существует пользователь с таким рефреш токеном
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            //генерация аксесс токена
            user.setAccess(generateAccessToken(user));
            //генерация рефреш токена
            user.setRefresh(generateRefreshToken());
            //сохранение токенов в бд
            userRepository.save(user);
            //создание ответа с новыми токенами
            AnswerGetToken answerGetToken = new AnswerGetToken();
            answerGetToken.error = false;
            answerGetToken.message = "success";
            answerGetToken.user = user;
            return new ObjectMapper().writeValueAsString(answerGetToken);
        }
        //создание ответа при неправильном рефреш токине
        Answer answer = new Answer();
        answer.error = true;
        answer.message = "Неправильный рефреш токен";
        return new ObjectMapper().writeValueAsString(answer);
    }

    public String sendMessage(String recipientUsername, String access) throws JsonProcessingException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        String[] strings = access.split("\\.");
        if (strings.length != 3) {
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Неправильный токен";
            return new ObjectMapper().writeValueAsString(answer);
        }
        long date = new Date().getTime();
        Map<String,String> testToJson = new ObjectMapper().readValue(baseDecode(strings[1]), Map.class);
        if(Long.parseLong(testToJson.get("exp")) < date){
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Ваш аксес токен устарел. Сгенерируйте новый";
            return new ObjectMapper().writeValueAsString(answer);
        }
        return null;
    }

    //генерация открытого и закрытого ключа
    public String generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();
        return keyPair.getPublic().toString();
        /*Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        byte[] data = cipher.doFinal("Hello!".getBytes());
        // Decrypt with PUBLIC KEY
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        byte[] result = cipher.doFinal(data);
        System.out.println(new String(result));*/
    }

    public String createUser(String username, String password) throws JsonProcessingException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        Optional<User> existingUser = userRepository.findByUsername(username);
        if(existingUser.isPresent()){
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Пользователь с таким username уже существует";
            return new ObjectMapper().writeValueAsString(answer);
        }
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setRefresh(generateRefreshToken());
        newUser.setAccess(generateAccessToken(newUser));
        newUser.setKey(generateKeys());
        userRepository.save(newUser);
        AnswerGetToken answerGetToken = new AnswerGetToken();
        answerGetToken.user = newUser;
        answerGetToken.message = "success";
        answerGetToken.error = false;
        return new ObjectMapper().writeValueAsString(answerGetToken);
    }
        /*if(strings[1])
        Optional<User> recipient = userRepository.findByUsername(recipientUsername);
    }*/
    /*
    public String generateKey() {
        long exp;
        int p = (int) (20 + Math.random()*40);
        int q = (int) (10 + Math.random()*30);
        long m = p * q;
        long fi = (long) (p - 1) *(q-1);
        long index = 3;
        while(true){
            if(fi%index!=0){
                exp = index;
                break;
            }
            index++;
            if(index > 10) return "null";
        }
        long d;
        index = 1;
        while(true){
            if(((index * exp) % fi) == 1){
                d = index;
                break;
            }
            index++;
            if(index > 100000) return "null";
        }
        return exp + " " + m + " " + d;
    }*/
}
