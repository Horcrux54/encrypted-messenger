package mess.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mess.DAO.UserDAO;
import mess.answers.Answer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@RestController
public class MessageController {
    UserDAO userDAO;

    public MessageController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @PostMapping({"new", "new/*"})
    public String newUser(@RequestHeader(value = "username", required = false, defaultValue = "null")String username,
                          @RequestHeader(value = "password", required = false, defaultValue = "null")String password) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {
        if(username.equals("null") || password.equals("null")){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Введите в заголовок запроса username и password"));
        }
        return userDAO.createUser(username, password);
    }
    //получение токенов
    @PostMapping({"/login/*", "/login"})
    public String base(@RequestHeader(value = "username", required = false, defaultValue = "null")String username,
                       @RequestHeader(value = "password", required = false, defaultValue = "null")String password,
                       @RequestHeader(value = "refresh", required = false, defaultValue = "null")String refresh) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {

        //сначала проверка идет по наличию заголовков имени и пароля, если все ок возврат токенов
        if(!username.equals("null") && !password.equals("null")) return userDAO.gettingToken(username, password);

        //проверка наличия в щаголовке запроса рефреш токена, если всё ок вернёт новые токены
        if(!refresh.equals("null")) return userDAO.generateTokens(refresh);

        //создание и отправка ошибочного ответа сервера
        return new ObjectMapper().writeValueAsString(new Answer(true, "Введите в заголовок запроса username и password или используйте рефреш токен"));

    }
    //Отправка сообщения
    @PostMapping({"/send/*", "/send"})
    public String sendMessage(@RequestHeader(value = "recipient", required = false, defaultValue = "null")String recipient,
                              @RequestHeader(value = "accessToken", required = false, defaultValue = "null")String access,
                              @RequestHeader(value = "message", required = false,defaultValue = "")String message) throws JsonProcessingException, NoSuchAlgorithmException, InvalidKeyException {

        //если не указан получатель
        if(recipient.equals("null")){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Введите в заголовке username отправителя"));
        }

        //если не указан access токен
        if(access.equals("null")){
            return new ObjectMapper().writeValueAsString(new Answer(true, "Введите в заголовке свой access токен"));
        }

        //если поле сообщение пустое
        if(message.equals("")){
            return new ObjectMapper().writeValueAsString(new Answer(true,"Введите в заголовок своё сообщение"));
        }

        //если все заголовки указаны
        return userDAO.sendMessage(recipient, access, message);
    }
}
