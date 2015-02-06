package antre.model;

import java.util.List;

public class Meal {
	
	private String id;
	private String name;
	private List<String> images;
	private Double price;
	
	
	public Meal(String id, String name, List<String> images, Double price) {
		super();
		this.id = id;
		this.name = name;
		this.images = images;
		this.price = price;
	}

	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<String> getImages() {
		return images;
	}
	public void setImages(List<String> images) {
		this.images = images;
	}

	public Double getPrice() {
		return price;
	}

	public void setPrice(Double price) {
		this.price = price;
	}

	@Override
	public String toString() {
		return "Meal [id=" + id + ", name=" + name + ", images=" + images
				+ ", price=" + price + "]";
	}
	
	
	
}
