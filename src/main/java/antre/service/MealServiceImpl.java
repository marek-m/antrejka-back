package antre.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import antre.dao.DayDao;
import antre.dao.MealDao;
import antre.db.Day;
import antre.db.Meal;
import antre.db.MealModel;
import antre.model.DayMenuModel;
import antre.model.googlesearchapi.GoogleSearchObject;

import com.google.gson.Gson;

@Service
public class MealServiceImpl implements MealService {

	@Autowired
	private SessionFactory sf;

	@Autowired
	MealDao mealDao;

	@Autowired
	DayDao dayDao;

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat(
			"dd-MM-yyyy");

	@Override
	public List<MealModel> getThisDay() {
		List<MealModel> result = new ArrayList<MealModel>();

		List<Meal> meals = mealDao.getThisDay();

		// TRANSLATe TO MODEL
		for (Meal m : meals) {
			String imgs = m.getImages();
			String[] urls = imgs.split("\\|");
			List<String> images = new ArrayList<String>();
			for (String url : urls) {
				images.add(url);
			}
			result.add(new MealModel(m.getId() + "", m.getName(), images,
					dateFormat.format(m.getDate()), m.getPrice()));
		}

		return result;
	}

	// TODO powielona metoda refaktor pozniej
	@Override
	public List<DayMenuModel> getThisWeek() {
		
		List<Day> days = dayDao.getThisWeek();
		List<DayMenuModel> result = new ArrayList<DayMenuModel>();
		System.out.println("DAYS:"+days.size());
		
		// TRANSLATe TO MODEL
		for (Day d : days) {
			DayMenuModel dayMenu = new DayMenuModel();
			List<MealModel> dayList = new ArrayList<MealModel>();
			dayMenu.setDate(d.getDate());
			
			for (Meal m : d.getMeals()) {
				String imgs = m.getImages();
				String[] urls = imgs.split("\\|");
				List<String> images = new ArrayList<String>();
				for (String url : urls) {
					images.add(url);
				}
				dayList.add(new MealModel(m.getId() + "", m.getName(), images, dateFormat.format(m.getDate()), m.getPrice()));
			}
			dayMenu.setMeals(dayList);
			result.add(dayMenu);
		}

		return result;
	}

	@Override
	public void initWeek() throws Exception {

		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

		Document doc = null;
		try {
			doc = Jsoup.connect("http://www.wantrejce.pl").get();
		} catch (IOException e) {
			// TODO problem with connection... probably. Do something!
			System.out.println("IOException:" + e.getMessage());
		}

		Elements days = doc.select("ul#subList2 li:matches(Pon|Wt|Śr|Czw|Pią)");
		List<Meal> meals = new ArrayList<Meal>();
		List<Day> daysInWeek = new ArrayList<Day>();

		System.out.println("Init days:"+days.size());
		for (Element day : days) {
			Elements daymenu = day.select("h4>p:has(strong)");
			Set<Meal> daymeals = new HashSet<Meal>();
			// soup
			Meal soup = new Meal(daymenu.first().text(), "", null, cal.getTime());

			Double mealPrice = 0.0d;
			Double mealWithoutSoup = 0.0d;
			System.out.println("Meals for day: "+daymenu.size());
			for (int i = 1; i < daymenu.size(); i++) {
				String mealName = daymenu.get(i).text();
				if (mealName.isEmpty())
					continue;
				if (mealName.startsWith("Cena")) {

					// TODO EXTRACT
					Matcher matcher = Pattern.compile("-?\\d+").matcher(
							mealName);
					List<Double> matches = new ArrayList<Double>();
					while (matcher.find()) {
						matches.add(Double.valueOf(matcher.group()));
					}
					mealPrice = matches.get(0);
					mealWithoutSoup = matches.get(1);
				} else {
					daymeals.add(new Meal(mealName, "", null, cal.getTime()));
				}
			}
			for (Meal m : daymeals) {
				m.setPrice(mealWithoutSoup);
			}
			//set price to soup and add to set
			soup.setPrice(mealPrice - mealWithoutSoup);
			daymeals.add(soup);
			
			Day dayInWeek = new Day();
			dayInWeek.setDate(cal.getTime());
			dayInWeek.setMeals(daymeals);

			daysInWeek.add(dayInWeek);

			meals.addAll(daymeals);
			// SET TO NEXT DAY
			cal.add(Calendar.DAY_OF_WEEK, 1);
		}
		// got meals for whole week
		// get images from google
		// TODO
		// for(Meal m : meals) {
		// GoogleSearchObject gso = getImagesForDescription(m.getName());
		// String images = "";
		// for(Item item : gso.getItems()) {
		// System.out.println("GOT IMAGE:"+item.getLink());
		// images += item.getLink() + "|";
		// }
		// m.setImages(images);
		// }

		Session session = sf.openSession();
		Transaction tx = session.beginTransaction();
		try {
			dayDao.saveList(daysInWeek, session);
			tx.commit();
			session.close();
		} catch (Exception e) {
			System.out.println("Exception:" + e.getMessage());
			tx.rollback();
			session.close();
			throw e;
		}

	}

	private GoogleSearchObject getImagesForDescription(String description)
			throws Exception {
		description = URLEncoder.encode(description, "UTF-8");
		String json = readUrl(createAddressForImageSearch(description));
		Gson gson = new Gson();
		GoogleSearchObject page = gson.fromJson(json, GoogleSearchObject.class);

		Thread.sleep(3500); // GOOGLE RESTRICTION 1request/user/1second
		return page;
	}

	private static String readUrl(String urlString) throws Exception {
		BufferedReader reader = null;
		try {
			URL url = new URL(urlString);
			reader = new BufferedReader(new InputStreamReader(url.openStream()));
			StringBuffer buffer = new StringBuffer();
			int read;
			char[] chars = new char[1024];
			while ((read = reader.read(chars)) != -1)
				buffer.append(chars, 0, read);

			return buffer.toString();
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	private static String createAddressForImageSearch(String description) {
		String cx = "016012965037667193616:tvoagxbs5sy";
		String cr = "pl";
		String q = description;
		String searchType = "image";
		int num = 5;
		String fields = "items/link";
		String key = "AIzaSyBGIvRqlYoec4l6n-TUoMsRV3qrb4XSlCI";

		String result = "https://www.googleapis.com/customsearch/v1?" + "cx="
				+ cx + "&" + "cr=pl&" + "q=" + description + "&"
				+ "searchType=" + searchType + "&" + "num=" + num + "&"
				+ "fields=" + fields + "&" + "key=" + key;

		return result;
	}

}
