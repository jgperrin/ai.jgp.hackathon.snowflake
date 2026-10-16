package ai.jgp.hackathon.snowflake.connection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Update;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.View;

@RestController
@RequestMapping("/api/me/connection")
class ConnectionController {

    private final ConnectionRegistry registry;

    ConnectionController(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    View get() {
        return registry.view(UserContext.current());
    }

    @PutMapping
    View save(@RequestBody Update update) {
        return registry.save(UserContext.current(), update);
    }
}
