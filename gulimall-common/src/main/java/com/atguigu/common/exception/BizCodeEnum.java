package com.atguigu.common.exception;

/**
 *
 *      错误码和错误信息定义类
 *          1、错误码定义规则为5位数字
 *          2、前两位标识业务场景，最后三位表示错误码。例如：10001： 10 通用； 001 系统未知异常
 *          3、维护错误码后需要维护错误描述，将他们定义为枚举形式
 *
 *      错误码列表：
 *          001：参数格式校验
 *
 *          11： 商品
 *          12：订单
 *          13：购物车
 *          14：物流
 *
 *
 */

public enum BizCodeEnum {

    UNKNOW_EXCEPTION(10000, "系统未知异常"),
    VALID_EXCEPTION(10001, "参数格式校验失败"),
    PRODUCT_UP_EXCEPTION(10002, "商品上架异常");

    private int code;
    private String msg;

    BizCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
