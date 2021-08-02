package jp.alhinc.calculate_sales;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculateSales_Commodity {

	// ファイル名
	private static final String FILE_NAME_BRANCH_LST = "branch.lst";
	private static final String FILE_NAME_BRANCH_OUT = "branch.out";
	private static final String FILE_NAME_COMMODITY_LST = "commodity.lst";
	private static final String FILE_NAME_COMMODITY_OUT = "commodity.out";

	// エラーメッセージ
	private static final String UNKNOWN_ERROR = "予期せぬエラーが発生しました";
	private static final String FILE_NOT_EXIST = "が存在しません";
	private static final String FILE_INVALID_FORMAT = "のフォーマットが不正です";
	private static final String BRANCH_INVALID_FORMAT = "の支店コードが不正です";
	private static final String COMMODITY_INVALID_FORMAT = "の商品コードが不正です";
	private static final String NOT_CONSECUTIVE_NUMBERS = "売上ファイル名が連番になっていません";
	private static final String TOTAL_AMOUNT_OVERFLOW = "合計金額が10桁を超えました";

	/**
	 * メインメソッド
	 *
	 * @param コマンドライン引数
	 */
	public static void main(String[] args) {
		// コマンドライン引数のチェック
		if (args.length != 1) {
			System.out.println(UNKNOWN_ERROR);
			return;
		}

		// 支店情報
		Map<String, String> branchNames = new HashMap<>();
		Map<String, Long> branchSales = new HashMap<>();

		// 商品情報
		Map<String, String> commodityNames = new HashMap<>();
		Map<String, Long> commoditySales = new HashMap<>();

		// 支店定義ファイル読み込み処理
		if(!readFile(args[0], FILE_NAME_BRANCH_LST, "^[0-9]{3}$", "支店定義ファイル", branchNames, branchSales)) {
			return;
		}

		// 商品定義ファイル読み込み処理
		if(!readFile(args[0], FILE_NAME_COMMODITY_LST, "^[0-9A-Za-z]{8}$", "商品定義ファイル", commodityNames, commoditySales)) {
			return;
		}

		// 売上ファイル読み込み処理
		File[] files = new File(args[0]).listFiles();

		List<File> rcdFiles = new ArrayList<>();
		for(int i = 0; i < files.length ; i++) {
			String fileName = files[i].getName();

			//ファイル、売上ファイル名のチェック
			if(files[i].isFile() && fileName.matches("^[0-9]{8}.rcd$")) {
				rcdFiles.add(files[i]);
			}
		}

		Collections.sort(rcdFiles);

		// 連番チェック
		for(int i = 0; i < rcdFiles.size() - 1; i++) {
			int former = Integer.parseInt(rcdFiles.get(i).getName().substring(0,8));
			int latter = Integer.parseInt(rcdFiles.get(i + 1).getName().substring(0,8));

			if((latter - former) != 1) {
				System.out.println(NOT_CONSECUTIVE_NUMBERS);
				return;
			}
		}

		BufferedReader br = null;
		for(int i = 0; i < rcdFiles.size(); i++) {
			try {
				br = new BufferedReader(new FileReader(rcdFiles.get(i)));
				ArrayList<String> fileContents = new ArrayList<>();

				String line = "";
				while((line = br.readLine()) != null) {
					fileContents.add(line);
				}

				String fileName = rcdFiles.get(i).getName();

				// 行数チェック
				if(fileContents.size() != 3) {
					System.out.println(fileName + FILE_INVALID_FORMAT);
					return;
				}

				String branchCode = fileContents.get(0);
				String commodityCode = fileContents.get(2);

				// 支店コードの存在チェック
				if (!branchNames.containsKey(branchCode)) {
					System.out.println(fileName + BRANCH_INVALID_FORMAT);
					return;
				}

				// 商品コードの存在チェック
				if (!commodityNames.containsKey(commodityCode)) {
					System.out.println(fileName + COMMODITY_INVALID_FORMAT);
					return;
				}

				//売上金額数字チェック
				if(!fileContents.get(2).matches("^[0-9]+$")) {
					System.out.println(UNKNOWN_ERROR);
					return;
				}

				//売上金額の加算
				long fileSale = Long.parseLong(fileContents.get(2));
				Long branchSaleAmount = branchSales.get(branchCode) + fileSale;
				Long commoditySaleAmount = commoditySales.get(commodityCode) + fileSale;

				if((branchSaleAmount >= 10000000000L) || (commoditySaleAmount >= 10000000000L)){
					System.out.println(TOTAL_AMOUNT_OVERFLOW);
					return;
				}

				branchSales.put(branchCode, branchSaleAmount);
				commoditySales.put(commodityCode, commoditySaleAmount);
			} catch(IOException e) {
				System.out.println(UNKNOWN_ERROR);
				return;
			} finally {
				if(br != null) {
					try {
						br.close();
					} catch(IOException e) {
						System.out.println(UNKNOWN_ERROR);
						return;
					}
				}
			}
		}

		// 支店別集計ファイル書き込み処理
		if(!writeFile(args[0], FILE_NAME_BRANCH_OUT, branchNames, branchSales)) {
			return;
		}

		// 商品別集計ファイル書き込み処理
		if(!writeFile(args[0], FILE_NAME_COMMODITY_OUT, commodityNames, commoditySales)) {
			return;
		}

	}

	/**
	 * 支店定義ファイル読み込み処理
	 *
	 * @param フォルダパス
	 * @param ファイル名
	 * @param 正規表現
	 * @param エラーメッセージ出力時の名称
	 * @param 支店コードと支店名を保持するMap
	 * @param 支店コードと売上金額を保持するMap
	 * @return 読み込み可否
	 */
	private static boolean readFile(String path, String fileName, String regex, String type,
			Map<String, String> names, Map<String, Long> sales) {

		BufferedReader br = null;
		try {
			File file = new File(path, fileName);

			//支店定義ファイル存在チェック
			if(!file.exists()) {
				System.out.println(type + FILE_NOT_EXIST);
				return false;
			}

			FileReader fr = new FileReader(file);
			br = new BufferedReader(fr);

			String line;
			// 一行ずつ読み込む
			while((line = br.readLine()) != null) {
				String[] items = line.split(",");

				//支店定義ファイルの行数、支店コードのチェック
				if((items.length != 2) || (!items[0].matches(regex))){
					System.out.println(type + FILE_INVALID_FORMAT);
					return false;
				}

				names.put(items[0], items[1]);
				sales.put(items[0], 0L);
			}

		} catch(IOException e) {
			System.out.println(UNKNOWN_ERROR);
			return false;
		} finally {
			// ファイルを開いている場合
			if(br != null) {
				try {
					// ファイルを閉じる
					br.close();
				} catch(IOException e) {
					System.out.println(UNKNOWN_ERROR);
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 支店別集計ファイル書き込み処理
	 *
	 * @param フォルダパス
	 * @param ファイル名
	 * @param 支店コードと支店名を保持するMap
	 * @param 支店コードと売上金額を保持するMap
	 * @return 書き込み可否
	 */
	private static boolean writeFile(String path, String fileName,
			Map<String, String> names, Map<String, Long> sales) {

		BufferedWriter bw = null;
		try {
			File file = new File(path, fileName);
			bw = new BufferedWriter(new FileWriter(file));

			for(String key : names.keySet()) {
				bw.write(key + "," + names.get(key) + "," + sales.get(key));
				bw.newLine();
			}
		} catch(IOException e) {
			System.out.println(UNKNOWN_ERROR);
			return false;
		} finally {
			if(bw != null) {
				try {
					bw.close();
				} catch(IOException e) {
					System.out.println(UNKNOWN_ERROR);
					return false;
				}
			}
		}
		return true;
	}

}
