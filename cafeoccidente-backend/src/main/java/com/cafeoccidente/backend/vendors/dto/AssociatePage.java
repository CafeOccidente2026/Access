package com.cafeoccidente.backend.vendors.dto;

/** Un registro por pantalla, como el formulario de Access: position es 0-based ("Registro n de total"). */
public record AssociatePage(long position, long total, AssociateResponse associate) {
}
