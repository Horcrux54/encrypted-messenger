package mess.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import mess.DAO.UserDAO;
import org.springframework.web.bind.annotation.*;

@RestController
public class MessageController {
    UserDAO userDAO;

    public MessageController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }


    //получение токенов
    @PostMapping({"/login/*", "/login"})
    public String base(@RequestHeader(value = "username", required = false, defaultValue = "null")String username,
                       @RequestHeader(value = "password", required = false, defaultValue = "null")String password,
                       @RequestHeader(value = "refresh", required = false, defaultValue = "null")String refresh) throws JsonProcessingException {
        //сначала проверка идет по наличию заголовков имени и пароля, если все ок возврат токенов
        if(!username.equals("null") && !password.equals("null")) return userDAO.gettingToken(username, password);
        //проверка наличия в щаголовке запроса рефреш токена, если всё ок вернёт новые токены
        if(!refresh.equals("null")) return userDAO.generateToken(refresh);
        //создание и отправка ошибочного ответа сервера
        else return "Отправьте в заголовок запроса username and pass";

    }
}
