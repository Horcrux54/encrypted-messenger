package mess.answers;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Answer {
    @JsonProperty("error")
    public boolean error;
    @JsonProperty("message")
    public String message;
}
