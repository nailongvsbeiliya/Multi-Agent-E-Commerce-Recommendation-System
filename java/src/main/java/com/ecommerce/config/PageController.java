package com.ecommerce.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/index.html", "/home.html"})
    public String home() {
        return "forward:/chat-ui.html";
    }
}
