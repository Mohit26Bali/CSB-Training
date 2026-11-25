package demo.web;

import demo.config.MailProperties;
import demo.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    private final OrderService orders;
    private final MailProperties mail;

    public HelloController(OrderService orders, MailProperties mail) {
        this.orders = orders;
        this.mail = mail;
    }

    @GetMapping("/order")
    public String order(@RequestParam String item) { return orders.place(item); }

    @GetMapping("/mail-host")
    public String mailHost() { return mail.host() + ":" + mail.port(); }
}
