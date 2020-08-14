package com.github.alissonmelonascimento.ifood.cadastro.dto;

import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.github.alissonmelonascimento.ifood.cadastro.Restaurante;
import com.github.alissonmelonascimento.ifood.cadastro.infra.DTO;

@ValidDTO
public class AdicionarRestauranteDTO implements DTO{
	
	@NotNull
    @Size(min = 3, max = 30)
	public String nomeFantasia;
    
	@NotNull
	@Pattern(regexp = "[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}\\/[0-9]{4}\\-[0-9]{2}")
    public String cnpj;
    
    public LocalizacaoDTO localizacao;
    
    @Override
    public boolean isValid(ConstraintValidatorContext constraintValidatorContext) {
        constraintValidatorContext.disableDefaultConstraintViolation();
        if (Restaurante.find("cnpj", cnpj).count() > 0) {
            constraintValidatorContext.buildConstraintViolationWithTemplate("CNPJ já cadastrado")
                    .addPropertyNode("cnpj")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

}
