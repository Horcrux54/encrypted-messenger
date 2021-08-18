package mess.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mess.DAO.UserDAO;
import mess.answers.Answer;
import mess.answers.AnswerGetToken;
import mess.entities.User;
import org.springframework.web.bind.annotation.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@RestController
public class MessageController {
    UserDAO userDAO;

    public MessageController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }
    @PostMapping({"new", "new/*"})
    public String newUser(@RequestHeader(value = "username", required = false, defaultValue = "null")String username,
                             @RequestHeader(value = "password", required = false, defaultValue = "null")String password) throws JsonProcessingException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        if(username.equals("null") || password.equals("null")){
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Введите в заголовок запроса username и password";
            return new ObjectMapper().writeValueAsString(answer);
        }
        return userDAO.createUser(username, password);
    }
    //получение токенов
    @PostMapping({"/login/*", "/login"})
    public String base(@RequestHeader(value = "username", required = false, defaultValue = "null")String username,
                       @RequestHeader(value = "password", required = false, defaultValue = "null")String password,
                       @RequestHeader(value = "refresh", required = false, defaultValue = "null")String refresh) throws JsonProcessingException, UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        //сначала проверка идет по наличию заголовков имени и пароля, если все ок возврат токенов
        if(!username.equals("null") && !password.equals("null")) return userDAO.gettingToken(username, password);
        //проверка наличия в щаголовке запроса рефреш токена, если всё ок вернёт новые токены
        if(!refresh.equals("null")) return userDAO.generateTokens(refresh);
        //создание и отправка ошибочного ответа сервера
        Answer answer = new Answer();
        answer.error = true;
        answer.message = "Введите в заголовок запроса username и password или используйте рефреш токен";
        return new ObjectMapper().writeValueAsString(answer);

    }
    //Отправка сообщения
    @PostMapping({"/send/*", "/send"})
    public String sendMessage(@RequestHeader(value = "recipient", required = false, defaultValue = "null")String recipient,
                              @RequestHeader(value = "accessToken", required = false, defaultValue = "null")String access) throws JsonProcessingException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        if(recipient.equals("null")){
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Введите в заголовке username отправителя";
            return new ObjectMapper().writeValueAsString(answer);
        }
        if(access.equals("null")){
            Answer answer = new Answer();
            answer.error = true;
            answer.message = "Введите в заголовке свой access токен";
            return new ObjectMapper().writeValueAsString(answer);
        }
        return userDAO.sendMessage(recipient, access);
    }
}
