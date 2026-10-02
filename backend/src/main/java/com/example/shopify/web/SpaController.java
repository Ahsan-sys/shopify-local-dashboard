package com.example.shopify.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({"/store", "/products", "/orders"})
  public String app() {
    return "forward:/index.html";
  }
}
