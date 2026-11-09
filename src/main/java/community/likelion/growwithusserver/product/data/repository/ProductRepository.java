package community.likelion.growwithusserver.product.data.repository;

import community.likelion.growwithusserver.product.data.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
