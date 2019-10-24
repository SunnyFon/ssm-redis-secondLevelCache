package com.sunnyfe.template.service;

import com.sunnyfe.template.pojo.User;

import java.util.List;

public interface UserService {
    List<User> findAll();
    void insertUser(User user);
    void updateUser(User user);
    void deleteUser(String id);
}
