package mess.repositories;

import mess.entities.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByRefresh(String refreshToken);
    Optional<User> findByAccess(String accessToken);
}
