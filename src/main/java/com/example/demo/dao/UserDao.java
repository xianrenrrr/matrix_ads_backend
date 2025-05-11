package com.example.demo.dao;

import com.example.demo.model.User;

public interface UserDao {
    User findByUsername(String username);
    User findByEmail(String email);
    void save(User user);
}
