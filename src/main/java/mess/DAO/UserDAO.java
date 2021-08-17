package mess.DAO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mess.answers.AnswerGetToken;
import mess.entities.User;
import mess.repositories.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

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
                answerGetToken.error = "false";
                answerGetToken.message = "success";
                answerGetToken.user = optionalUser.get();
                return new ObjectMapper().writeValueAsString(answerGetToken);
            }
        }
        //создание ошибочного ответа
        AnswerGetToken answerGetToken = new AnswerGetToken();
        answerGetToken.error = "true";
        answerGetToken.message = "Неправильно введен username или password";
        answerGetToken.user = null;
        return new ObjectMapper().writeValueAsString(answerGetToken);
    }

    //генерация рефреш токена
    public static String generateString() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        return uuid1.concat(uuid2).concat(uuid3).replace("-","");
    }

    //проверка рефреш токена и генерация новых токенов
    public String generateToken(String refresh) throws JsonProcessingException {
        Optional<User> optionalUser = userRepository.findByRefresh(refresh);
        //если существует пользователь с таким рефреш токеном
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            //генерация аксесс токена
            user.setAccess(generateString());
            //генерация рефреш токена
            user.setRefresh(generateString());
            //сохранение токенов в бд
            userRepository.save(user);
            //создание ответа с новыми токенами
            AnswerGetToken answerGetToken = new AnswerGetToken();
            answerGetToken.error = "false";
            answerGetToken.message = "success";
            answerGetToken.user = user;
            return new ObjectMapper().writeValueAsString(answerGetToken);
        }
        //создание ответа при неправильном рефреш токине
        AnswerGetToken answerGetToken = new AnswerGetToken();
        answerGetToken.error = "true";
        answerGetToken.message = "Неправильный рефреш токен";
        answerGetToken.user = null;
        return new ObjectMapper().writeValueAsString(answerGetToken);
    }
}
