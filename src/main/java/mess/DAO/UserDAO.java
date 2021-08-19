package mess.DAO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mess.answers.Answer;
import mess.answers.AnswerGetToken;
import mess.answers.PayloadAccess;
import mess.entities.Message;
import mess.entities.User;
import mess.repositories.MessageRepository;
import mess.repositories.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class UserDAO {
    private final Environment environment;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public UserDAO(Environment environment, UserRepository userRepository, MessageRepository messageRepository) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
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
    private String generateHash(String string) throws NoSuchAlgorithmException, InvalidKeyException {
        //получение секретного ключа с файла пропертис
        String key = environment.getProperty("message.userTable.password");
        //установка алгоритма шифрования
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        assert key != null;
        sha256_HMAC.init(new SecretKeySpec(key.getBytes(), "HmacSHA256"));
        //шифрование
        byte[] bytes = sha256_HMAC.doFinal(string.getBytes(StandardCharsets.UTF_8));
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
        return new ObjectMapper().writeValueAsString(new Answer(true, "Неправильно введен username или password"));
    }

    //генерация рефреш токена
    public String generateRefreshToken() {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        return uuid1.concat(uuid2).concat(uuid3).replace("-","");
    }
    //геренация аксес токена
    public String generateAccessToken(User user) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {

        //создание заголовка токена(он всегда один)
        String header = baseEncoding("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

        //создание полезные данные
        PayloadAccess payloadAccess = new PayloadAccess();

        //устанавливаем username
        payloadAccess.setUsername(user.getUsername());

        //устанавливаем время жизни токена
        payloadAccess.setExp(String.valueOf(new Date().getTime()/1000/60 + 10));
        String payout = baseEncoding(new ObjectMapper().writeValueAsString(payloadAccess));

        //конкатенируем заголовок и полезные данные
        String concatHeaderPayout = header.concat(".").concat(payout);

        //Создаем подпись. Строка с заголовком и полезными данными кодируется и переводится в Base64
        String signature = baseEncoding(generateHash(concatHeaderPayout));

        //отправка токена
        return concatHeaderPayout.concat(".").concat(signature);
    }

    //проверка рефреш токена и генерация новых токенов
    public String generateTokens(String refresh) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {
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
        return new ObjectMapper().writeValueAsString(new Answer(true, "Неправильный рефреш токен"));
    }

    //Проверка подписи
    private boolean signatureVerification(String[] accessToken) throws NoSuchAlgorithmException, InvalidKeyException {
        return baseEncoding(generateHash(accessToken[0].concat(".").concat(accessToken[1]))).equals(accessToken[2]);
    }

    //Проверка полей и отправка сообщений
    public String sendMessage(String recipientUsername, String access, String message) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {

        //разбиение на header payload signature
        String[] strings = access.split("\\.");

        //если разбиение произошло неудачно
        if (strings.length != 3) {
            return new ObjectMapper().writeValueAsString(new Answer(true, "Неправильный токен"));
        }

        //если access токен неверный(проверка подписи)
        if(!signatureVerification(strings)){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Ваш аксес токен не верный. Сгенерируйте новый"));
        }

        //получение времени в минутах
        long date = new Date().getTime()/1000/60;

        //конвертация payload в json
        Map<String,String> testToJson = new ObjectMapper().readValue(baseDecode(strings[1]), Map.class);

        //если access токен устарел
        if(Long.parseLong(testToJson.get("exp")) < date){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Ваш аксес токен устарел. Сгенерируйте новый"));
        }

        //если получателя не существует
        if(!userRepository.existsByUsername(recipientUsername)){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Такой пользователь не существует"));
        }

        //Формирование объекта и сохранение его в таблицу
        Message newMessage = new Message();
        newMessage.setId(messageRepository.count()+1);
        newMessage.setTime(date);
        newMessage.setSender(testToJson.get("username"));
        newMessage.setRecipient(recipientUsername);
        newMessage.setMess(message);
        messageRepository.save(newMessage);
        return new ObjectMapper().writeValueAsString(new Answer(false, "Ваше сообщение было доставлено"));
    }

    //создание нового пользователя
    public String createUser(String username, String password) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {

        //если пользователь с таким username существует
        if(userRepository.existsByUsername(username)){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Пользователь с таким username уже существует"));
        }

        User newUser = new User();
        newUser.setId(userRepository.count()+1);
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setRefresh(generateRefreshToken());
        newUser.setAccess(generateAccessToken(newUser));
        newUser.setKey("pass");
        userRepository.save(newUser);

        //формирование ответа
        AnswerGetToken answerGetToken = new AnswerGetToken();
        answerGetToken.user = newUser;
        answerGetToken.message = "success";
        answerGetToken.error = false;
        return new ObjectMapper().writeValueAsString(answerGetToken);
    }
}
