
package acme.features.sponsor.sponsorship;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.client.data.models.Dataset;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractService;
import acme.client.views.SelectChoices;
import acme.entities.invoice.Invoice;
import acme.entities.project.Project;
import acme.entities.sponsorship.Sponsorship;
import acme.entities.sponsorship.SponsorshipType;
import acme.entities.systemconf.SystemConfiguration;
import acme.roles.Sponsor;

@Service
public class SponsorSponsorshipPublishService extends AbstractService<Sponsor, Sponsorship> {

	@Autowired
	private SponsorSponsorshipRepository repository;


	@Override
	public void authorise() {
		boolean status;
		int masterId;
		Sponsorship sponsorship;
		Sponsor sponsor;

		masterId = super.getRequest().getData("id", int.class);
		sponsorship = this.repository.findOneSponsorshipById(masterId);
		sponsor = sponsorship == null ? null : sponsorship.getSponsor();
		status = sponsorship != null && sponsorship.isDraftMode() && super.getRequest().getPrincipal().hasRole(sponsor);

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {
		Sponsorship object;
		int id;

		id = super.getRequest().getData("id", int.class);
		object = this.repository.findOneSponsorshipById(id);

		super.getBuffer().addData(object);
	}

	@Override
	public void bind(final Sponsorship object) {
		assert object != null;

		int projectId;
		Project project;

		projectId = super.getRequest().getData("project", int.class);
		project = this.repository.findOneProjectById(projectId);

		super.bind(object, "code", "moment", "durationStart", "durationEnd", "amount", "type", "emailContact", "link", "project");
		object.setProject(project);

	}

	@Override
	public void validate(final Sponsorship object) {
		assert object != null;

		if (!super.getBuffer().getErrors().hasErrors("code")) {
			Sponsorship existing;

			existing = this.repository.findOneSponsorshipByCode(object.getCode());
			if (!object.getCode().equals(existing.getCode()))
				super.state(existing == null, "code", "sponsor.sponsorship.form.error.duplicated");
		}

		if (!super.getBuffer().getErrors().hasErrors("durationStart"))
			super.state(MomentHelper.isAfter(object.getDurationStart(), object.getMoment()), "durationStart", "sponsor.sponsorship.form.error.duration-start-date-not-valid");

		if (!super.getBuffer().getErrors().hasErrors("durationEnd")) {
			Date durationStart;
			Date durationEnd;
			boolean isMinimumDuration;
			boolean durationEndIsAfterStart;

			durationStart = object.getDurationStart();
			durationEnd = object.getDurationEnd();
			isMinimumDuration = MomentHelper.isLongEnough(durationStart, durationEnd, 1, ChronoUnit.MONTHS);
			durationEndIsAfterStart = MomentHelper.isAfter(durationEnd, durationStart);

			super.state(isMinimumDuration && durationEndIsAfterStart, "durationStart", "sponsor.sponsorship.form.error.duration-not-valid");
		}

		if (!super.getBuffer().getErrors().hasErrors("amount")) {
			super.state(object.getAmount().getAmount() > 0, "amount", "sponsor.sponsorship.form.error.amount-must-be-positive");
			List<SystemConfiguration> sc = this.repository.findSystemConfiguration();
			final boolean foundCurrency = Stream.of(sc.get(0).acceptedCurrencies.split(",")).anyMatch(c -> c.equals(object.getAmount().getCurrency()));

			super.state(foundCurrency, "amount", "sponsor.sponsorship.form.error.currency-not-supported");
		}
		{
			Collection<Invoice> invoices;
			double sponsorshipAmount;
			double invoicesTotalAmount;

			invoices = this.repository.findAllInvoicesBySponsorshipId(object.getId());
			sponsorshipAmount = object.getAmount().getAmount();
			invoicesTotalAmount = invoices.stream().mapToDouble(i -> i.totalAmount()).sum();

			super.state(sponsorshipAmount == invoicesTotalAmount, "*", "sponsor.sponsorship.form.error.sponsorship-amount-and-invoices-total-amount-not-equal");
		}
	}

	@Override
	public void perform(final Sponsorship object) {
		assert object != null;

		object.setDraftMode(false);
		this.repository.save(object);
	}

	@Override
	public void unbind(final Sponsorship object) {
		assert object != null;

		SelectChoices choices;
		SelectChoices projectsChoices;
		Collection<Project> projects;

		Dataset dataset;
		choices = SelectChoices.from(SponsorshipType.class, object.getType());
		projects = this.repository.findAllProjects();
		projectsChoices = SelectChoices.from(projects, "code", object.getProject());
		dataset = super.unbind(object, "code", "moment", "durationStart", "durationEnd", "amount", "type", "emailContact", "link", "draftMode", "project");

		dataset.put("sponsorshipType", choices);
		dataset.put("project", projectsChoices.getSelected().getKey());
		dataset.put("projects", projectsChoices);
		super.getResponse().addData(dataset);

	}
}
