package community.likelion.growwithusserver.product.data.exception;

import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.common.response.GlobalException;

public class ProductNotFoundException extends GlobalException {
    public ProductNotFoundException() {
        super(ErrorCode.DATA_NOT_FOUND);
    }
}