package playwright;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.*;
import java.io.Serializable;
import org.quartz.SchedulerFactory;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.Scheduler;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.CronScheduleBuilder;
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.SimpleScheduleBuilder.*;


public class Main {
	public static void main(String[] args) {
		try {
			SchedulerFactory schedulerFactory = new StdSchedulerFactory();
			Scheduler scheduler = schedulerFactory.getScheduler();
			JobDetail job = JobBuilder.newJob(PlaywrightScheduler.class)
									  .withIdentity("scrappingTrigger", "group1")
									  .build();
			Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity("scrappingTrigger", "group1")
//					.withSchedule(CronScheduleBuilder.cronSchedule("0 0/2 * 1/1 * ? *"))
					.build();
			
			scheduler.scheduleJob(job, trigger);
			scheduler.start();
			System.out.println("Scheduler started...");
			
		}
		catch (SchedulerException se) {
			se.printStackTrace();
		}
	};
}
