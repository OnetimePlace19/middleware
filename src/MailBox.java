import java.time.LocalDateTime;
import java.util.*;

public class MailBox implements IMailBox {

    private final Map<String, List<String>> _Users;

    public MailBox() {
        this._Users = new HashMap<>();
    }

    @Override
    public boolean UserExist(String userName) {
        return _Users.containsKey(userName);
    }

    @Override
    public List<String> ViewUser() {
        return new ArrayList<>(_Users.keySet());
    }

    @Override
    public String GetLastMessage(String User) {
        return _Users.get(User).getLast();
    }

    @Override
    public List<String> ViewUserMessage(String userName) {
        return _Users.getOrDefault(userName, List.of());
    }

    @Override
    public void AddMessage(String sender, String receiver, String message) {
        if (!_Users.containsKey(receiver)) {
            throw new IllegalArgumentException("User Doesn't Exict : " + receiver);
        }

        _Users.get(receiver).add("from " + sender + ": '" +message+ "' at " + LocalDateTime.now());
    }

    @Override
    public void CreateUserBox(String userName) {
        _Users.putIfAbsent(userName, new ArrayList<>());
    }

    @Override
    public void RemoveUser(String userName) {
        _Users.remove(userName);
    }


}
