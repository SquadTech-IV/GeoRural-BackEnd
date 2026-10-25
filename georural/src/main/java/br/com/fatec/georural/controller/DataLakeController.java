package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.DataLakeStatusResponse;
import br.com.fatec.georural.service.DataLakeStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/datalake")
public class DataLakeController {

    private final DataLakeStatusService statusService;

    public DataLakeController(DataLakeStatusService statusService) {
        this.statusService = statusService;
    }


    @GetMapping("/status")
    public DataLakeStatusResponse status() {
        return statusService.verificar();
    }
}