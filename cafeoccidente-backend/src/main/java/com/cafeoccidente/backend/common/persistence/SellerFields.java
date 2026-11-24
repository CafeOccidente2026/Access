package com.cafeoccidente.backend.common.persistence;

/** Nombres y direccion del vendedor que guarda cada compra (Seco, Verde y Pasilla); Lombok genera estos metodos. */
public interface SellerFields {

    String getFirstName();

    void setFirstName(String firstName);

    String getLastName();

    void setLastName(String lastName);

    String getAddress();

    void setAddress(String address);
}
