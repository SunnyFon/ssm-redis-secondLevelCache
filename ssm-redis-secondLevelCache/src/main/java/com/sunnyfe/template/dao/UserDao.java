package com.sunnyfe.template.dao;

import com.sunnyfe.template.pojo.User;

import java.util.List;

public interface UserDao {
    List<User> findAll();
    void insertUser(User user);
    void updateUser(User user);
    void deleteUser(String id);
}
