package com.github.alissonmelonascimento.ifood.cadastro.dto;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.github.alissonmelonascimento.ifood.cadastro.infra.DTO;

public class ValidDTOValidator
        implements ConstraintValidator<ValidDTO, DTO> {

    @Override
    public void initialize(ValidDTO constraintAnnotation) {
    }

    @Override
    public boolean isValid(DTO dto, ConstraintValidatorContext constraintValidatorContext) {
        return dto.isValid(constraintValidatorContext);
    }
}