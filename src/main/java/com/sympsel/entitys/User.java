package com.sympsel.entitys;

import java.util.ArrayList;
import com.sympsel.entitys.enums.Permission;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "uuid")
public class User {
    private String uuid;
    private String name;
    private String password;
    private Permission permission;
    private long createTime;
    private ArrayList<String> tags;
    private String introduction;
}
