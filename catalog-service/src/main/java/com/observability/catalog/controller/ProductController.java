package com.observability.catalog.controller;

import com.observability.catalog.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Map<String, Object>> readAll() {
        return productService.readAll();
    }

    @GetMapping("/{id}")
    public Map<String, Object> readById(@PathVariable int id) {
        return productService.readById(id);
    }
}
