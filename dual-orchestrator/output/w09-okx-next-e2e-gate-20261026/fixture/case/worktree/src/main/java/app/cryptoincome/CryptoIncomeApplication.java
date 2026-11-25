package app.cryptoincome;

import app.cryptoincome.cli.SyncMarketCliRunner;
import app.cryptoincome.cli.AssessIncomeCliRunner;
import app.cryptoincome.cli.SyncProductCliRunner;
import app.cryptoincome.cli.GridCliRunner;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@MapperScan({
        "app.cryptoincome.run.infrastructure.mybatis",
        "app.cryptoincome.funding.infrastructure.mybatis",
        "app.cryptoincome.assessment.infrastructure.mybatis",
        "app.cryptoincome.grid.infrastructure.mybatis",
        "app.cryptoincome.grid.sync.infrastructure",
        "app.cryptoincome.market.infrastructure.mybatis",
        "app.cryptoincome.shadow.infrastructure.mybatis"
})
@SpringBootApplication
public class CryptoIncomeApplication {

    public static void main(String[] args) {
        if (args.length > 0 && (SyncMarketCliRunner.COMMAND.equals(args[0])
                || AssessIncomeCliRunner.COMMAND.equals(args[0])
                || SyncProductCliRunner.COMMAND.equals(args[0])
                || GridCliRunner.COMMAND.equals(args[0]))) {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(CryptoIncomeApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(args);
            System.exit(SpringApplication.exit(context));
            return;
        }
        SpringApplication.run(CryptoIncomeApplication.class, args);
    }
}
