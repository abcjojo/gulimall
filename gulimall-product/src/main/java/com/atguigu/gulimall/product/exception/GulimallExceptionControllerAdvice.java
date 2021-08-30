package com.atguigu.gulimall.product.exception;


import com.atguigu.common.exception.BizCodeEnum;
import com.atguigu.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
//@ResponseBody       // json 数据格式返回
//@ControllerAdvice(basePackages = "com.atguigu.gulimall.product.controller")   // 标注处理哪些controller
//      @RestControllerAdvice = @ResponseBody + @ControllerAdvice(basePackages
@RestControllerAdvice(basePackages = "com.atguigu.gulimall.product.controller")
public class GulimallExceptionControllerAdvice {



    @ExceptionHandler(value = MethodArgumentNotValidException.class)      // 标注需要处理哪些异常
    public R handleValidException(MethodArgumentNotValidException e) {
        log.error("数据校验出现问题：{}, 异常类型：{}", e.getMessage(), e.getClass());

        BindingResult bindingResult = e.getBindingResult();
        Map<String, String> errorMap = new HashMap<>();
        bindingResult.getFieldErrors().forEach(fieldError -> errorMap.put(fieldError.getField(), fieldError.getDefaultMessage()));

        return R.error(BizCodeEnum.VALID_EXCEPTION.getCode(), BizCodeEnum.VALID_EXCEPTION.getMsg()).put("data", errorMap);
    }

    @ExceptionHandler(value = Throwable.class)      // 标注需要处理哪些异常
    public R handleException(Throwable throwable) {


        return R.error();
    }
}
