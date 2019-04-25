package com.handlers;

import com.dao.DataBaseService;

public class ConfigHandler {

    private DataBaseService dao = DataBaseService.getInstance();

    private ConfigHandler() {
    }

    /**
     * Static Factory method of ConfigHandler class
     *
     * @return singleton instance
     */
    public static synchronized ConfigHandler getInstance() {
        return AccountHandlerInstanceContainer.instance;
    }

    public double getM() {
        return dao.getM();
    }

    public void setM(String m) {
        dao.setM(Double.parseDouble(m));
    }

    public double getS() {
        return dao.getS();
    }

    public void setS(String s) {
        dao.setS(Double.parseDouble(s));
    }

    /**
     * This method ensures that the same singleton reference is returned when
     * the object is serialized and the deserialized back
     *
     * @return singleton instance
     */
    protected Object readResolve() {
        return AccountHandlerInstanceContainer.instance;
    }

    /**
     * Private static instance holder.
     */
    private static final class AccountHandlerInstanceContainer {
        static final ConfigHandler instance = new ConfigHandler();
    }
}
