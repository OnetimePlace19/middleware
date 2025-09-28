import java.util.List;

public interface IMailBox {


    void CreateUserBox(String User);

    void AddMessage(String Sender,String Reciever,String Message);

    String GetLastMessage(String User);

    boolean UserExist(String User);

    void RemoveUser(String User);

    List<String> ViewUser();

    List<String> ViewUserMessage(String User);


}
