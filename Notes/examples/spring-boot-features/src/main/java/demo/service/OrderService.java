package demo.service;

import demo.aop.Timed;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Timed
    public String place(String item) {
        if (item.isBlank()) throw new IllegalArgumentException("empty item");
        return "ordered " + item;
    }

    public String placeTwice(String item) {
        // self-invocation: this call does NOT go through the proxy, so @Timed on place() is skipped here
        return place(item) + ", " + place(item);
    }
}
