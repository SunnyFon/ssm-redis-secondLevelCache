package com.sunnyfe.template.service;

import com.sunnyfe.template.dao.UserDao;
import com.sunnyfe.template.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {


    @Autowired
    private UserDao dao;

    @Override
    public List<User> findAll() {
        return dao.findAll();
    }

    @Override
    public void insertUser(User user) {
        dao.insertUser(user);
    }

    @Override
    public void updateUser(User user) {
        dao.updateUser(user);
    }

    @Override
    public void deleteUser(String id) {
        dao.deleteUser(id);
    }
}
