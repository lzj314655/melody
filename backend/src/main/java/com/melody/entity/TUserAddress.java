package com.melody.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户收货地址表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_address")
public class TUserAddress extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 收货人 */
    private String name;

    /** 收货人手机 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 地址标签 (家/公司/学校) */
    private String tag;

    /** 是否默认地址 1是 0否 */
    private Integer isDefault;
}