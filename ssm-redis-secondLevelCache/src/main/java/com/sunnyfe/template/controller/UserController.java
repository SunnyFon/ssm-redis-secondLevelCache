package com.sunnyfe.template.controller;

import com.sunnyfe.template.pojo.User;
import com.sunnyfe.template.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//以下代码为方便测试缓存编写, 毫无意义
@RestController
@RequestMapping(value = "/user")
public class UserController {

    @Autowired
    private UserService service;

    //调用查询 会添加缓存
    @RequestMapping(value = "/findAll", method = RequestMethod.GET)
    public Map<String ,String> findAllUser() {
        List<User> userList = service.findAll();
        Map<String, String> params = new HashMap<>();
        for (User user : userList) {
        params.put("id",user.getId());
        params.put("userName", user.getUserName());
        params.put("password", user.getPassword());
        }
        return params;
    }

    // insert update delete都会清除缓存
    @RequestMapping(value = "/insertUser", method = RequestMethod.GET)
    public String insertUser() {
        User user = new User();
        user.setId("3");
        user.setPassword("123");
        user.setUserName("大力");
        try {
            service.insertUser(user);
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "once again";
    }

    @RequestMapping(value = "/updateUser", method = RequestMethod.GET)
    public String updateUser() {
        User user = new User();
        user.setId("3");
        user.setPassword("1234");
        user.setUserName("大力");
        try {
            service.updateUser(user);
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "once again";
    }

    @RequestMapping(value = "/deleteUser", method = RequestMethod.GET)
    public String deleteUser() {
        String id = "3";
        try {
            service.deleteUser(id);
            return "ok";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "once again";
    }
}
