package dev.aurora.contexto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Permissão exigida por um endpoint (no método ou na classe; o do método prevalece). Quem
 * não a tem em nenhuma unidade recebe 403; quem tem, opera só nas unidades onde ela foi
 * concedida: o escopo da requisição é restringido a elas (RN-13, ADR 0013).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface ExigePermissao {

	String value();

}
