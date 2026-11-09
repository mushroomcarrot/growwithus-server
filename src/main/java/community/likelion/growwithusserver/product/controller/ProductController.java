package community.likelion.growwithusserver.product.controller;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import community.likelion.growwithusserver.product.data.dto.ProductRequestDto;
import community.likelion.growwithusserver.product.data.dto.ProductResponseDto;
import community.likelion.growwithusserver.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public GlobalResponse<Void> createProduct(@Valid @RequestBody ProductRequestDto productRequestDto) {
        return productService.createProduct(SecurityUtil.getUsername(), productRequestDto);
    }

    @GetMapping
    public GlobalResponse<ProductResponseDto> getProduct(@RequestParam Long id) {
        return productService.getProduct(id);
    }
}
