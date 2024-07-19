package com.example.bluetooththermalprinterjava;


public class ConnectionClass {
    private String printer_name;

    // Constructor to initialize printer_name
    public ConnectionClass() {
        this.printer_name = "";
    }

    // Getter for printer_name
    public String getPrinterName() {
        return printer_name;
    }

    // Setter for printer_name
    public void setPrinterName(String printer_name) {
        this.printer_name = printer_name;
    }
}
