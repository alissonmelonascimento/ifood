package com.github.alissonmelonascimento.ifood.cadastro;



import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlow;
import org.eclipse.microprofile.openapi.annotations.security.OAuthFlows;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import com.github.alissonmelonascimento.ifood.cadastro.dto.AdicionarPratoDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.AdicionarRestauranteDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.AtualizarPratoDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.AtualizarRestauranteDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.PratoDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.PratoMapper;
import com.github.alissonmelonascimento.ifood.cadastro.dto.RestauranteDTO;
import com.github.alissonmelonascimento.ifood.cadastro.dto.RestauranteMapper;
import com.github.alissonmelonascimento.ifood.cadastro.infra.ConstraintViolationResponse;

@Path("/restaurantes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "restaurante")
@RolesAllowed("proprietario")//por isso tivemos que colocar a role dentro de GROUPS la no keycloak
@SecurityScheme(securitySchemeName = "ifood-oauth", type = SecuritySchemeType.OAUTH2, flows = @OAuthFlows(password = @OAuthFlow(tokenUrl = "http://localhost:8180/auth/realms/ifood/protocol/openid-connect/token")))
@SecurityRequirements(value = {@SecurityRequirement(name = "ifood-oauth", scopes = {})})
//@SecurityRequirement(name = "ifood-oauth")//esta linha da crash no quarkus. use a de cima
public class RestauranteResource {
	
	@Inject
	RestauranteMapper restauranteMapper;
	
	@Inject
	PratoMapper pratoMapper;
	
    @Inject
    @Channel("restaurantes")
    Emitter<String> emitter;
    
    @Inject
    JsonWebToken jwt;//permite pegar qq campo do token

    @Inject
    @Claim(standard = Claims.sub)
    String sub;//injeta um campo expecifico do toke. nesse caso, o nome do usuario
	
	@GET
	@Counted(name = "Quantidade lista todos os restaurantes")
	@SimplyTimed(name="Tempo simples de lista todos os restaurantes")
	@Timed(name="Tempo completo de lista todos os restaurantes")
	public List<RestauranteDTO> getAll(){
		Stream<Restaurante> restaurantes = Restaurante.streamAll();
		return restaurantes.map(r -> restauranteMapper.toRestauranteDTO(r)).collect(Collectors.toList());
	}
	
	@POST
	@Transactional
    @APIResponse(responseCode = "201", description = "Caso restaurante seja cadastrado com sucesso")
    @APIResponse(responseCode = "400", content = @Content(schema = @Schema(allOf = ConstraintViolationResponse.class)))
	public Response insert(@Valid AdicionarRestauranteDTO dto) {
		Restaurante restaurante = restauranteMapper.toRestaurante(dto);
		restaurante.proprietario = sub;
		restaurante.persist();
		
		Jsonb create = JsonbBuilder.create();
		String json = create.toJson(restaurante);
		
		// pode comentar a linha abaixo, pois o connect(debezium), que esta monitorando
		// as entradas do banco de dados, enviaria para o kafka
		emitter.send(json);
		
		return Response.status(Status.CREATED).build();
	}
	
	@PUT
	@Path("{id}")
	@Transactional
	public void update(@PathParam("id") Long id, AtualizarRestauranteDTO dto) {
		
		Optional<Restaurante> opt = Restaurante.findByIdOptional(id);
		if(!opt.isPresent()) {
			throw new NotFoundException();
		}
		
		Restaurante restaurante = opt.get();
		
        if (!restaurante.proprietario.equals(sub)) {
            throw new ForbiddenException();
        }		
		
		restauranteMapper.toRestaurante(dto, restaurante);
		
		restaurante.persist();
	}
	
	@DELETE
	@Transactional
	public void delete(@PathParam("id") Long id) {
		
		Optional<Restaurante> opt = Restaurante.findByIdOptional(id);
		
		opt.ifPresentOrElse(Restaurante::delete, () -> {
			throw new NotFoundException();
		});
		
        if (!opt.get().proprietario.equals(sub)) {
            throw new ForbiddenException();
        }

        opt.get().delete();
	}
	
	//================ pratos =======================
	@GET
	@Path("{idRestaurante}/pratos")
	@Tag(name = "prato")
	public List<PratoDTO> getAllPratos(@PathParam("idRestaurante") Long idRestaurante){
		
        Optional<Restaurante> restauranteOp = Restaurante.findByIdOptional(idRestaurante);
        if (restauranteOp.isEmpty()) {
            throw new NotFoundException("Restaurante não existe");
        }
        Stream<Prato> pratos = Prato.stream("restaurante", restauranteOp.get());
        return pratos.map(p -> pratoMapper.toDTO(p)).collect(Collectors.toList());
	}
	
	@POST
	@Path("{idRestaurante}/pratos")
	@Tag(name = "prato")
	@Transactional
	public Response insert(@PathParam("idRestaurante") Long idRestaurante, AdicionarPratoDTO dto) {
        Optional<Restaurante> restauranteOp = Restaurante.findByIdOptional(idRestaurante);
        if (restauranteOp.isEmpty()) {
            throw new NotFoundException("Restaurante não existe");
        }
        //        //Utilizando dto, recebi detached entity passed to persist:
        //        Prato prato = new Prato();
        //        prato.nome = dto.nome;
        //        prato.descricao = dto.descricao;
        //
        //        prato.preco = dto.preco;
        //        prato.restaurante = restauranteOp.get();
        //        prato.persist();

        Prato prato = pratoMapper.toPrato(dto);
        prato.restaurante = restauranteOp.get();
        prato.persist();
        return Response.status(Status.CREATED).build();
	}
	
	@PUT
	@Path("{idRestaurante}/pratos/{id}")
	@Tag(name = "prato")
	@Transactional
	public void update(@PathParam("idRestaurante") Long idRestaurante, @PathParam("id") Long id, AtualizarPratoDTO dto) {
		
        Optional<Restaurante> restauranteOp = Restaurante.findByIdOptional(idRestaurante);
        if (restauranteOp.isEmpty()) {
            throw new NotFoundException("Restaurante não existe");
        }

        //No nosso caso, id do prato vai ser único, independente do restaurante...
        Optional<Prato> pratoOp = Prato.findByIdOptional(id);

        if (pratoOp.isEmpty()) {
            throw new NotFoundException("Prato não existe");
        }
        Prato prato = pratoOp.get();
        pratoMapper.toPrato(dto, prato);
        prato.persist();
	}
	
	@DELETE
	@Path("{idRestaurante}/pratos/{id}")
	@Tag(name = "prato")
	@Transactional
	public void delete(@PathParam("idRestaurante") Long idRestaurante, @PathParam("id") Long id) {
		
		Optional<Restaurante> optRest = Restaurante.findByIdOptional(idRestaurante);
		if(!optRest.isPresent()) {
			throw new NotFoundException("Restaurante não existe");
		}		
		
		Optional<Prato> opt = Prato.findByIdOptional(id);
		
		opt.ifPresentOrElse(Prato::delete, () -> {
			throw new NotFoundException("Prato não existe");
		});

	}	
	
}
