# Sample video 
[https://github.com/user-attachments/assets/2220cefd-e1fb-4d3d-a18f-4bd8e87fe8b1](https://github.com/user-attachments/assets/a7362786-044f-4c43-aae4-f3c78ade1068)

# Overall diagram
![image](https://github.com/user-attachments/assets/b4ce9b66-f280-4718-8de5-426b5ff9dd6b)

# Diagram breakdown
```Notes```: This project is mainly used to scrape the SKUs (Products) from the public E-commerce website Carrefour. Its main goal is to schedule the pipeline, can be implemented on cloud-base system, to periodically scrape the data from the website.
1. ```Ecommerce_brand``` table:
This is the table containing ```Unique``` information of the ```Brand``` from which SKUs belong to. This table will only add in ```new brands``` in upcoming scrape.
* Dimentions breakdown:
  * ```id```: The unique id for each brand
  * ```Brand```: The actual name of that brand displayed on the E-commerce platform
3. ```Ecommerce_sku``` table:
This is the main table containing ```Unique``` information of the SKUs being scrapped. This table will only add in ```new SKUs``` in upcoming scrape.
* Dimentions breakdown:
  * ```id```: The unique id for each SKU
  * ```fk_brand_id```: The ```foreign brand id key``` used to get the ```Brand name``` from the ```Ecommerce_brand``` table
  * ```SKU_ID```: The generated marketplace code for each product
  * ```KeyWord```: The keyword the users used to launch the scrape in the executed job
  * ```Product```: The actual name of SKU's products displayed on the E-commerce platforms
  * ```Url```: The products' urls from which the users can access directly
  * ```img_url```: The products' images urls from which the users can access directly
  * ```Base_size```: The quantified base size of each product, if obtainable from the marketplace
  * ```source```: The data source that these products get created (From scrapping in this project)
  * ```Created```: The date time that the products were inserted to this table
3. ```Ecommerce_sku_prices``` table:
This is the table containing information of the SKU's prices being scrapped. This table will be ```updated``` for existed SKUs and ```inserted``` for non-existed SKUs in upcoming scrape.
* Dimentions breakdown:
  * ```id```: The unique id for each row
  * ```SKU_ID```: The generated marketplace code for each product
  * ```Price```: The actual price of the products displayed on the E-commerce platforms
  *  ```created```: The date time that the products were inserted to this table
  *  ```updated```: The date time that the products were updated to this table
  *  ```fk_sku_id```: The ```foreign key``` used to link with the ```id``` primary key in the ```ecommerce_sku``` table
4. ```Ecommerce_sku_ratings``` table:
This is the table containing information of the SKU's prices being scrapped. This table will be ```updated``` for existed SKUs and ```inserted``` for non-existed SKUs in upcoming scrape.
* Dimentions breakdown:
  * ```id```: The unique id for each row
  * ```SKU_ID```: The generated marketplace code for each product
  * ```Ratings```: The actual ratings of the products displayed on the E-commerce platforms
  * ```Stars```: The actual stars of the products displayed on the E-commerce platforms
  * ```created```: The date time that the products were inserted to this table
  * ```updated```: The date time that the products were updated to this table
  * ```fk_sku_id```: The ```foreign key``` used to link with the ```id``` primary key in the ```ecommerce_sku``` table
5. ```Ecommerce_sku_stamp``` table:
This is the table containing information of all the SKU's prices being scrapped. It will be ```appended``` all SKUs, regardless of existed or non-existed in each scrape, its main purpose is used for references and trackings.
* Dimentions breakdown:
  * ```id```: The unique id for each row
  * ```SKU_ID```: The generated marketplace code for each product
  * ```fk_sku_id```: The ```foreign key``` used to link with the ```id``` primary key in the ```ecommerce_sku``` table
  * ```Ratings```: The actual ratings of the products displayed on the E-commerce platforms
  * ```Stars```: The actual stars of the products displayed on the E-commerce platforms
  * ```created```: The date time that the products were inserted to this table
  * ```updated```: The date time that the products were updated to this table
