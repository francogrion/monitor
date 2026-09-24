package com.service;

import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    private volatile double m;
    private volatile double s;

    public double getM() {
        return m;
    }

    public void setM(String m) {
        this.m = Double.parseDouble(m);
    }

    public double getS() {
        return s;
    }

    public void setS(String s) {
        this.s = Double.parseDouble(s);
    }
}
