package mess.answers;

import com.fasterxml.jackson.annotation.JsonProperty;
import mess.entities.User;
//Ответ от сервера с токенами
public class AnswerGetToken {
    @JsonProperty("error")
    public String error;
    @JsonProperty("message")
    public String message;
    @JsonProperty("token")
    public User user;
}
