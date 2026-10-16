import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AnhsTeamFieldInspectorApp());
}

class AnhsTeamFieldInspectorApp extends StatefulWidget {
  const AnhsTeamFieldInspectorApp({super.key});

  @override
  State<AnhsTeamFieldInspectorApp> createState() => _AnhsTeamFieldInspectorAppState();
}

class _AnhsTeamFieldInspectorAppState extends State<AnhsTeamFieldInspectorApp> {
  bool _isDark = true;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: "Anh's TEAM - Giám Sát Hiện Trường",
      debugShowCheckedModeBanner: false,
      themeMode: _isDark ? ThemeMode.dark : ThemeMode.light,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorSchemeSeed: const Color(0xFF1D4ED8),
        scaffoldBackgroundColor: const Color(0xFFF6F8FC),
        dividerTheme: const DividerThemeData(thickness: 0.8, space: 1),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorSchemeSeed: const Color(0xFF3B82F6),
        scaffoldBackgroundColor: const Color(0xFF0B1021),
        dividerTheme: const DividerThemeData(thickness: 0.8, space: 1),
      ),
      home: FieldInspectorHomeScreen(
        isDark: _isDark,
        onToggleTheme: () => setState(() => _isDark = !_isDark),
      ),
    );
  }
}

class FieldChecklistItem {
  String discipline;
  String title;
  String standard;
  String measured;
  bool passed;

  FieldChecklistItem({
    required this.discipline,
    required this.title,
    required this.standard,
    required this.measured,
    this.passed = true,
  });

  Map<String, dynamic> toJson() => {
        'discipline': discipline,
        'title': title,
        'standard': standard,
        'measured': measured,
        'passed': passed,
      };
}

class FieldInspectorHomeScreen extends StatefulWidget {
  final bool isDark;
  final VoidCallback onToggleTheme;

  const FieldInspectorHomeScreen({
    super.key,
    required this.isDark,
    required this.onToggleTheme,
  });

  @override
  State<FieldInspectorHomeScreen> createState() => _FieldInspectorHomeScreenState();
}

class _FieldInspectorHomeScreenState extends State<FieldInspectorHomeScreen> {
  static const MethodChannel _channel = MethodChannel('com.anhsteam.fieldinspector/native');

  Map<String, dynamic> _status = {};
  List<Map<String, dynamic>> _stampedPhotos = [];
  List<Map<String, dynamic>> _recordings = [];
  List<Map<String, dynamic>> _exportedDocx = [];
  Timer? _pollTimer;

  String _selectedSpeechLocale = 'vi-VN';
  String _lastSyncedSpeech = '';
  String _selectedPhotoPath = '';

  // Controllers cho thông tin đóng dấu ảnh hiện trường & Biên bản nghiệm thu
  final TextEditingController _projectCtrl = TextEditingController(
    text: 'Tòa nhà Trung tâm R&D Công nghệ (DA01-IBST)',
  );
  final TextEditingController _workItemCtrl = TextEditingController(
    text: 'Kiểm tra cốt thép & lớp bê tông bảo vệ dầm sàn',
  );
  final TextEditingController _zoneAxisCtrl = TextEditingController(
    text: 'Tầng hầm B1 - Trục (B-C)/(3-4) - Cao độ -4.50m',
  );
  final TextEditingController _gpsCompassCtrl = TextEditingController(
    text: '21.004512°N, 105.798234°E (±1.5m RTK) | Hướng: 135° Đông Nam',
  );
  final TextEditingController _measuredResultCtrl = TextEditingController(
    text: 'Thép D25 CB500-V; Lớp bảo vệ a = 26mm (ĐẠT - TCVN 4453)',
  );
  final TextEditingController _inspectorCtrl = TextEditingController(
    text: 'KS. Nguyễn Văn A (TVGS) - KS. Trần Văn B (NTTC)',
  );

  // Controllers cho thông tin hành chính của Biên bản .docx
  final TextEditingController _orgNameCtrl = TextEditingController(
    text: "ANH'S TEAM - KHẢO SÁT & GIÁM SÁT HIỆN TRƯỜNG",
  );
  final TextEditingController _docNumCtrl = TextEditingController(
    text: 'Số: 01/2026/BBKT-HT',
  );
  final TextEditingController _locDateCtrl = TextEditingController(
    text: 'Hà Nội, ngày 16 tháng 10 năm 2026',
  );
  final TextEditingController _reportTitleCtrl = TextEditingController(
    text: 'BIÊN BẢN KIỂM TRA, KHẢO SÁT VÀ NGHIỆM THU CÔNG VIỆC XÂY DỰNG TẠI HIỆN TRƯỜNG',
  );
  final TextEditingController _startTimeCtrl = TextEditingController(
    text: '08 giờ 30 phút ngày 16/10/2026',
  );
  final TextEditingController _endTimeCtrl = TextEditingController(
    text: '11 giờ 30 phút cùng ngày',
  );
  final TextEditingController _supervisorCtrl = TextEditingController(
    text: 'KS. Nguyễn Văn A - Giám sát trưởng thi công xây dựng',
  );
  final TextEditingController _contractorCtrl = TextEditingController(
    text: 'KS. Trần Văn B - Chỉ huy trưởng / Phụ trách kỹ thuật thi công trực tiếp',
  );

  // Controllers thêm nhanh chỉ tiêu kiểm tra mới tại Tab 2
  String _newDiscipline = 'Kết cấu BTCT';
  final TextEditingController _newCheckTitleCtrl = TextEditingController();
  final TextEditingController _newCheckStdCtrl = TextEditingController(
    text: 'TCVN 4453:1995; Điều 22 NĐ 207/2026/NĐ-CP',
  );
  final TextEditingController _newCheckMeasuredCtrl = TextEditingController();

  final List<FieldChecklistItem> _checklistItems = [
    FieldChecklistItem(
      discipline: 'Khảo sát & Trắc địa',
      title: 'Kiểm tra hệ thống lưới trắc địa và cao độ chuẩn tại trục (A-D)/(1-6)',
      standard: 'TCVN 9398:2012; NĐ 217/2026/NĐ-CP (Sai số <= ±5 mm)',
      measured: 'Sai lệch thực đo bằng máy toàn đạc điện tử: +2.0 mm',
      passed: true,
    ),
    FieldChecklistItem(
      discipline: 'Kết cấu BTCT',
      title: 'Kiểm tra đường kính, khoảng cách và chiều dài nối chồng cốt thép dầm sàn',
      standard: 'TCVN 4453:1995; TCVN 5574:2018 (Thép D25 CB500-V, nối >= 40d)',
      measured: 'Đúng chủng loại D25 CB500-V, chiều dài nối thực đo 1020 mm',
      passed: true,
    ),
    FieldChecklistItem(
      discipline: 'Kết cấu BTCT',
      title: 'Kiểm tra chiều dày lớp bê tông bảo vệ cốt thép chịu lực bằng con kê định hình',
      standard: 'TCVN 4453:1995 Bảng 6 (Dầm a = 25±5 mm; Sàn a = 15±3 mm)',
      measured: 'Lớp bảo vệ dầm thực đo đạt 25-27 mm; sàn đạt 15-16 mm',
      passed: true,
    ),
    FieldChecklistItem(
      discipline: 'Vật liệu hiện trường',
      title: 'Kiểm tra độ sụt hỗn hợp bê tông thương phẩm B30 và lấy mẫu tại hiện trường',
      standard: 'TCVN 3106:2022; Điều 22 NĐ 207/2026/NĐ-CP (Độ sụt 160±20 mm)',
      measured: 'Độ sụt thực đo: 165 mm; đã lấy 03 tổ mẫu có ký xác nhận',
      passed: true,
    ),
    FieldChecklistItem(
      discipline: 'Cơ điện & PCCC',
      title: 'Kiểm tra ống chờ xuyên vách và vật liệu chèn bịt ngăn cháy trục kỹ thuật',
      standard: 'Luật 55/2024/QH15; NĐ 347/2026/NĐ-CP; QCVN 06:2022/BXD (EI 60)',
      measured: 'Ống lồng thép đặt đúng thiết kế, vật liệu ngăn cháy đủ tem KĐ',
      passed: true,
    ),
  ];

  final List<String> _conclusions = [
    'Các công việc khảo sát trắc địa, lắp dựng cốt thép và đặt ống chờ kỹ thuật tại khu vực Tầng hầm B1 đã được thực hiện phù hợp với hồ sơ thiết kế bản vẽ thi công được duyệt và các quy chuẩn, tiêu chuẩn kỹ thuật hiện hành.',
    'Đồng ý nghiệm thu công việc xây dựng theo quy định tại Điều 22 Nghị định số 207/2026/NĐ-CP và cho phép Nhà thầu thi công chuyển bước thực hiện công tác tiếp theo.',
  ];

  @override
  void initState() {
    super.initState();
    _initData();
    _pollTimer = Timer.periodic(const Duration(milliseconds: 900), (_) => _pollStatus());
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _initData() async {
    await _pollStatus();
    await _refreshAllLists();
  }

  Future<void> _pollStatus() async {
    try {
      final res = await _channel.invokeMethod('getSystemStatus');
      if (res is Map && mounted) {
        final map = Map<String, dynamic>.from(res);
        final finalSpeech = (map['latestFinalSpeech'] ?? '').toString();
        if (finalSpeech.isNotEmpty && finalSpeech != _lastSyncedSpeech) {
          _lastSyncedSpeech = finalSpeech;
          _measuredResultCtrl.text = finalSpeech;
          _newCheckMeasuredCtrl.text = finalSpeech;
        }
        setState(() {
          _status = map;
        });
      }
    } catch (_) {}
  }

  Future<void> _refreshAllLists() async {
    try {
      final photosRes = await _channel.invokeMethod('listStampedPhotos');
      final recRes = await _channel.invokeMethod('listRecordings');
      final docRes = await _channel.invokeMethod('listExportedDocx');
      if (mounted) {
        setState(() {
          if (photosRes is List) {
            _stampedPhotos = photosRes.map((e) => Map<String, dynamic>.from(e as Map)).toList();
            if (_selectedPhotoPath.isEmpty && _stampedPhotos.isNotEmpty) {
              _selectedPhotoPath = (_stampedPhotos.first['path'] ?? '').toString();
            }
          }
          if (recRes is List) {
            _recordings = recRes.map((e) => Map<String, dynamic>.from(e as Map)).toList();
          }
          if (docRes is List) {
            _exportedDocx = docRes.map((e) => Map<String, dynamic>.from(e as Map)).toList();
          }
        });
      }
    } catch (_) {}
  }

  Map<String, String> _currentStampArgs() => {
        'projectName': _projectCtrl.text.trim(),
        'workItem': _workItemCtrl.text.trim(),
        'zoneAxis': _zoneAxisCtrl.text.trim(),
        'gpsAndCompass': _gpsCompassCtrl.text.trim(),
        'measuredResult': _measuredResultCtrl.text.trim(),
        'inspectorName': _inspectorCtrl.text.trim(),
      };

  Future<void> _refreshGpsAndCompass() async {
    try {
      final res = await _channel.invokeMethod('refreshGpsAndCompass');
      if (res is Map && mounted) {
        final combined = (res['combined'] ?? '').toString();
        if (combined.isNotEmpty) {
          setState(() {
            _gpsCompassCtrl.text = combined;
          });
        }
        _showSnack('Đã cập nhật tọa độ GNSS/GPS & góc phương vị La bàn.');
      }
    } catch (e) {
      _showSnack('Lỗi đọc cảm biến GPS/La bàn: $e');
    }
  }

  Future<void> _captureCameraAndStamp() async {
    try {
      final res = await _channel.invokeMethod('captureCameraAndStamp', _currentStampArgs());
      if (res is Map) {
        final path = (res['path'] ?? '').toString();
        await _refreshAllLists();
        if (path.isNotEmpty && mounted) {
          setState(() => _selectedPhotoPath = path);
        }
        _showSnack('Đã chụp và dán Bảng thông tin kỹ thuật hiện trường vào ảnh!');
      }
    } catch (e) {
      _showSnack('Thông báo Camera: $e');
    }
  }

  Future<void> _pickGalleryAndStamp() async {
    try {
      final res = await _channel.invokeMethod('pickGalleryAndStamp', _currentStampArgs());
      if (res is Map) {
        final path = (res['path'] ?? '').toString();
        await _refreshAllLists();
        if (path.isNotEmpty && mounted) {
          setState(() => _selectedPhotoPath = path);
        }
        _showSnack('Đã dán Bảng thông tin định vị & SHA-256 vào ảnh đã chọn!');
      }
    } catch (e) {
      _showSnack('Lỗi chọn ảnh: $e');
    }
  }

  Future<void> _generateDemoStampedPhoto() async {
    try {
      final res = await _channel.invokeMethod('generateDemoStampedPhoto', _currentStampArgs());
      if (res is Map) {
        final path = (res['path'] ?? '').toString();
        await _refreshAllLists();
        if (path.isNotEmpty && mounted) {
          setState(() => _selectedPhotoPath = path);
        }
        _showSnack('Đã tạo ảnh đóng dấu kỹ thuật hiện trường (lưu tại /Pictures/AnhsTeam_HienTruong)!');
      }
    } catch (e) {
      _showSnack('Lỗi tạo ảnh đóng dấu: $e');
    }
  }

  Future<void> _toggleFieldAudioRecording() async {
    final isRec = _status['isRecording'] == true;
    try {
      if (isRec) {
        await _channel.invokeMethod('stopFieldRecording');
        await _refreshAllLists();
        _showSnack('Đã lưu tệp ghi âm hiện trường WAV kèm mã băm SHA-256.');
      } else {
        final gain = (_status['roomGainBoost'] as num?)?.toDouble() ?? 2.5;
        await _channel.invokeMethod('startFieldRecording', {
          'noteTitle': _workItemCtrl.text.trim(),
          'roomGainBoost': gain,
        });
        _showSnack('Đang thu âm thuyết minh hiện trường (16kHz PCM WAV)...');
      }
      await _pollStatus();
    } catch (e) {
      _showSnack('Thông báo thu âm: $e');
    }
  }

  Future<void> _toggleVoiceDictation() async {
    final isListening = _status['isListeningSpeech'] == true;
    try {
      if (isListening) {
        await _channel.invokeMethod('stopVoiceDictation');
      } else {
        await _channel.invokeMethod('clearSpeechBuffer');
        _lastSyncedSpeech = '';
        await _channel.invokeMethod('startVoiceDictation', {
          'locale': _selectedSpeechLocale,
        });
      }
      await _pollStatus();
    } catch (e) {
      _showSnack('Thông báo nhận diện giọng nói: $e');
    }
  }

  Future<void> _exportFieldInspectionDocx() async {
    try {
      final payload = {
        'docLanguageMode': 'VI',
        'organizationName': _orgNameCtrl.text.trim(),
        'documentNumber': _docNumCtrl.text.trim(),
        'locationDate': _locDateCtrl.text.trim(),
        'reportTitle': _reportTitleCtrl.text.trim(),
        'projectName': _projectCtrl.text.trim(),
        'zoneAxis': _zoneAxisCtrl.text.trim(),
        'gpsCoords': _gpsCompassCtrl.text.trim(),
        'startTime': _startTimeCtrl.text.trim(),
        'endTime': _endTimeCtrl.text.trim(),
        'supervisorName': _supervisorCtrl.text.trim(),
        'contractorName': _contractorCtrl.text.trim(),
        'checklistItems': _checklistItems.map((e) => e.toJson()).toList(),
        'stampedPhotos': _stampedPhotos,
        'conclusions': _conclusions,
      };
      final res = await _channel.invokeMethod('generateFieldReportDocx', {
        'jsonPayload': jsonEncode(payload),
      });
      await _refreshAllLists();
      if (res is Map) {
        final dlPath = (res['publicDownloadPath'] ?? res['path'] ?? '').toString();
        _showSnack('Đã xuất Biên bản Hiện trường (.docx) tại: $dlPath');
      }
    } catch (e) {
      _showSnack('Lỗi xuất Biên bản .docx: $e');
    }
  }

  Future<void> _shareFile(String path, String mimeType) async {
    await _channel.invokeMethod('shareFile', {
      'path': path,
      'mimeType': mimeType,
    });
  }

  Future<void> _deleteFile(String path) async {
    await _channel.invokeMethod('deleteFile', {'path': path});
    if (_selectedPhotoPath == path) {
      _selectedPhotoPath = '';
    }
    await _refreshAllLists();
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 3)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cardBg = widget.isDark ? const Color(0xFF151E32) : Colors.white;
    final borderCol = widget.isDark ? const Color(0xFF2A3655) : const Color(0xFFD5DFEE);

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: cardBg,
          titleSpacing: 12,
          title: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.asset(
                  'assets/logo_anhs_team.png',
                  width: 38,
                  height: 38,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => const Icon(Icons.engineering, size: 32),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Anh's TEAM - Khảo Sát & Giám Sát Hiện Trường",
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      'NĐ 207/2026/NĐ-CP • NĐ 217/2026/NĐ-CP • NĐ 347/2026/NĐ-CP',
                      style: TextStyle(
                        fontSize: 11,
                        color: widget.isDark ? Colors.blue.shade200 : Colors.blue.shade800,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
          actions: [
            IconButton(
              tooltip: 'Cấp quyền Camera / GPS / Mic',
              onPressed: () => _channel.invokeMethod('requestPermissions'),
              icon: const Icon(Icons.verified_user_outlined),
            ),
            IconButton(
              tooltip: 'Đổi giao diện Sáng / Tối',
              onPressed: widget.onToggleTheme,
              icon: Icon(widget.isDark ? Icons.light_mode : Icons.dark_mode),
            ),
          ],
          bottom: const TabBar(
            isScrollable: false,
            tabs: [
              Tab(icon: Icon(Icons.add_a_photo_outlined, size: 20), text: '1. Ảnh Đóng Dấu'),
              Tab(icon: Icon(Icons.fact_check_outlined, size: 20), text: '2. Checklist & Âm'),
              Tab(icon: Icon(Icons.description_outlined, size: 20), text: '3. Biên Bản .DOCX'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            _buildTab1PhotoStamper(cardBg, borderCol),
            _buildTab2ChecklistAndVoice(cardBg, borderCol),
            _buildTab3DocxAndArchive(cardBg, borderCol),
          ],
        ),
      ),
    );
  }

  // ===========================================================================
  // TAB 1: CHỤP ẢNH HIỆN TRƯỜNG & DÁN BẢNG THÔNG TIN KỸ THUẬT LÊN ẢNH
  // ===========================================================================
  Widget _buildTab1PhotoStamper(Color cardBg, Color borderCol) {
    final compassLabel = (_status['compassLabel'] ?? '135° Đông Nam (SE)').toString();
    final gpsText = (_status['gpsText'] ?? '21.004512°N, 105.798234°E').toString();

    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        // Single-Level Card duy nhất cho khu vực cấu hình và chụp ảnh đóng dấu
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: borderCol),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Thanh nhấn lề trái (Left Accent Bar) thay vì đóng hộp lồng kép
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  border: const Border(left: BorderSide(color: Color(0xFF3B82F6), width: 3.5)),
                  color: const Color(0xFF3B82F6).withOpacity(0.10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'CHỤP ẢNH HIỆN TRƯỜNG & DÁN CỨNG BẢNG THÔNG TIN ĐỊNH VỊ LÊN ẢNH',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Cảm biến trực tuyến: $gpsText  •  La bàn: $compassLabel',
                      style: const TextStyle(fontSize: 12),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              _buildCompactField('1. Tên Dự án / Mã công trình:', _projectCtrl),
              _buildCompactField('2. Hạng mục kiểm tra / khảo sát / nghiệm thu:', _workItemCtrl),
              _buildCompactField('3. Vị trí Khu vực / Trục định vị / Cao độ:', _zoneAxisCtrl),
              Row(
                children: [
                  Expanded(
                    child: _buildCompactField('4. Tọa độ GNSS/GPS & Hướng La bàn:', _gpsCompassCtrl),
                  ),
                  const SizedBox(width: 8),
                  FilledButton.tonalIcon(
                    onPressed: _refreshGpsAndCompass,
                    icon: const Icon(Icons.my_location, size: 18),
                    label: const Text('Lấy GPS'),
                  ),
                ],
              ),
              _buildCompactField('5. Kết quả đo thực tế & Đánh giá (TCVN/QCVN):', _measuredResultCtrl),
              _buildCompactField('6. Cán bộ hiện trường (TVGS & Nhà thầu):', _inspectorCtrl),

              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.icon(
                    onPressed: _captureCameraAndStamp,
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Mở Camera & Đóng Dấu'),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: _pickGalleryAndStamp,
                    icon: const Icon(Icons.photo_library_outlined),
                    label: const Text('Chọn Ảnh & Đóng Dấu'),
                  ),
                  OutlinedButton.icon(
                    onPressed: _generateDemoStampedPhoto,
                    icon: const Icon(Icons.auto_fix_high),
                    label: const Text('Tạo Ảnh Đóng Dấu Ngay'),
                  ),
                ],
              ),

              // Đường kẻ phân cách ngang cho phần xem trước ảnh đã đóng dấu
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Divider(),
              ),
              const Text(
                'ẢNH CHỨNG CỨ HIỆN TRƯỜNG ĐÃ DÁN BẢNG THÔNG TIN & MÃ BĂM SHA-256:',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 8),

              if (_selectedPhotoPath.isNotEmpty && File(_selectedPhotoPath).existsSync()) ...[
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.file(
                    File(_selectedPhotoPath),
                    key: ValueKey(_selectedPhotoPath),
                    fit: BoxFit.contain,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Tệp: ${_selectedPhotoPath.split(Platform.pathSeparator).last}',
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: () => _shareFile(_selectedPhotoPath, 'image/jpeg'),
                      icon: const Icon(Icons.share, size: 17),
                      label: const Text('Chia sẻ Ảnh (Zalo/Email)'),
                    ),
                  ],
                ),
              ] else
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Text('Hãy nhấn "Mở Camera & Đóng Dấu" hoặc "Tạo Ảnh Đóng Dấu Ngay" để xem ảnh chứng cứ.'),
                ),
            ],
          ),
        ),
      ],
    );
  }

  // ===========================================================================
  // TAB 2: DANH MỤC KIỂM TRA (CHECKLIST) & GHI ÂM HIỆN TRƯỜNG
  // ===========================================================================
  Widget _buildTab2ChecklistAndVoice(Color cardBg, Color borderCol) {
    final total = _checklistItems.length;
    final passed = _checklistItems.where((e) => e.passed).length;
    final failed = total - passed;
    final rate = total > 0 ? (passed * 100.0 / total).toStringAsFixed(1) : '100.0';
    final isRec = _status['isRecording'] == true;
    final elapsed = _status['elapsedSeconds'] ?? 0;
    final isListening = _status['isListeningSpeech'] == true;
    final partialSpeech = (_status['latestPartialSpeech'] ?? '').toString();

    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: borderCol),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Thanh tổng hợp tự động tỷ lệ tuân thủ kỹ thuật
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  border: Border(
                    left: BorderSide(
                      color: failed == 0 ? const Color(0xFF22C55E) : const Color(0xFFEF4444),
                      width: 3.5,
                    ),
                  ),
                  color: (failed == 0 ? const Color(0xFF22C55E) : const Color(0xFFEF4444)).withOpacity(0.10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'TỔNG HỢP CHECKLIST HIỆN TRƯỜNG: ĐẠT $passed/$total CHỈ TIÊU ($rate%)',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      failed == 0
                          ? 'Kết luận tự động: ĐỦ ĐIỀU KIỆN CHUYỂN BƯỚC / NGHIỆM THU (Điều 22 NĐ 207/2026/NĐ-CP)'
                          : 'Cảnh báo: Có $failed chỉ tiêu KHÔNG ĐẠT — Yêu cầu lập phiếu NCR khắc phục!',
                      style: const TextStyle(fontSize: 12),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Công cụ thu âm ghi chú hiện trường & đọc giọng nói số liệu
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: isRec ? Colors.red.shade700 : const Color(0xFF2563EB),
                      ),
                      onPressed: _toggleFieldAudioRecording,
                      icon: Icon(isRec ? Icons.stop_circle_outlined : Icons.mic),
                      label: Text(isRec ? 'Dừng Thu WAV (${elapsed}s)' : 'Ghi Âm Hiện Trường (.WAV)'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: FilledButton.tonalIcon(
                      onPressed: _toggleVoiceDictation,
                      icon: Icon(isListening ? Icons.hearing_disabled : Icons.record_voice_over),
                      label: Text(isListening ? 'Đang nghe ($_selectedSpeechLocale)...' : 'Đọc Số Liệu Giọng Nói'),
                    ),
                  ),
                ],
              ),
              if (partialSpeech.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text('Đang nhận diện: $partialSpeech', style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic)),
                ),

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(),
              ),

              const Text(
                'DANH MỤC KIỂM TRA CHUYÊN NGÀNH TẠI HIỆN TRƯỜNG:',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 6),

              for (int i = 0; i < _checklistItems.length; i++) ...[
                if (i > 0) const Divider(height: 16),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Checkbox(
                      value: _checklistItems[i].passed,
                      onChanged: (v) => setState(() => _checklistItems[i].passed = v ?? true),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${i + 1}. [${_checklistItems[i].discipline}] ${_checklistItems[i].title}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '• Yêu cầu / Tiêu chuẩn: ${_checklistItems[i].standard}',
                            style: const TextStyle(fontSize: 12),
                          ),
                          Text(
                            '• Thực đo hiện trường: ${_checklistItems[i].measured}',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: _checklistItems[i].passed ? Colors.green.shade400 : Colors.red.shade400,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 19),
                      onPressed: () => setState(() => _checklistItems.removeAt(i)),
                    ),
                  ],
                ),
              ],

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(),
              ),
              const Text(
                'THÊM NHANH CHỈ TIÊU KIỂM TRA / ĐO ĐẠC HIỆN TRƯỜNG:',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: _newDiscipline,
                decoration: const InputDecoration(
                  labelText: 'Chuyên ngành kiểm tra',
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
                items: const [
                  DropdownMenuItem(value: 'Khảo sát & Trắc địa', child: Text('Khảo sát & Trắc địa (NĐ 217/2026)')),
                  DropdownMenuItem(value: 'Kết cấu BTCT', child: Text('Kết cấu BTCT & Thép (TCVN 4453 / 5574)')),
                  DropdownMenuItem(value: 'Vật liệu hiện trường', child: Text('Vật liệu & Thí nghiệm NDT')),
                  DropdownMenuItem(value: 'Cơ điện (MEP)', child: Text('Hệ thống Cơ điện MEP (TCVN 7447 / 5687)')),
                  DropdownMenuItem(value: 'Cơ điện & PCCC', child: Text('An toàn PCCC (Luật 55/2024 & NĐ 347/2026)')),
                ],
                onChanged: (v) {
                  if (v != null) setState(() => _newDiscipline = v);
                },
              ),
              const SizedBox(height: 8),
              _buildCompactField('Nội dung kiểm tra tại hiện trường:', _newCheckTitleCtrl),
              _buildCompactField('Tiêu chuẩn / Thiết kế yêu cầu:', _newCheckStdCtrl),
              _buildCompactField('Số liệu thực đo tại hiện trường:', _newCheckMeasuredCtrl),
              FilledButton.tonalIcon(
                onPressed: () {
                  if (_newCheckTitleCtrl.text.trim().isEmpty) {
                    _showSnack('Vui lòng nhập nội dung kiểm tra hiện trường.');
                    return;
                  }
                  setState(() {
                    _checklistItems.add(
                      FieldChecklistItem(
                        discipline: _newDiscipline,
                        title: _newCheckTitleCtrl.text.trim(),
                        standard: _newCheckStdCtrl.text.trim(),
                        measured: _newCheckMeasuredCtrl.text.trim().isEmpty
                            ? 'Đạt yêu cầu theo thiết kế được duyệt'
                            : _newCheckMeasuredCtrl.text.trim(),
                        passed: true,
                      ),
                    );
                    _newCheckTitleCtrl.clear();
                    _newCheckMeasuredCtrl.clear();
                  });
                  _showSnack('Đã thêm chỉ tiêu vào Bảng kiểm tra hiện trường.');
                },
                icon: const Icon(Icons.add_task),
                label: const Text('Thêm Chỉ Tiêu Vào Bảng Kiểm Tra'),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ===========================================================================
  // TAB 3: XUẤT BIÊN BẢN HIỆN TRƯỜNG (.DOCX) & KHO LƯU TRỮ CHỨNG CỨ
  // ===========================================================================
  Widget _buildTab3DocxAndArchive(Color cardBg, Color borderCol) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: cardBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: borderCol),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  border: const Border(left: BorderSide(color: Color(0xFF3B82F6), width: 3.5)),
                  color: const Color(0xFF3B82F6).withOpacity(0.10),
                ),
                child: const Text(
                  'XUẤT BIÊN BẢN KIỂM TRA, KHẢO SÁT & NGHIỆM THU (.DOCX) — CHUẨN NĐ 30/2020 & NĐ 207/2026',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                ),
              ),
              const SizedBox(height: 12),

              _buildCompactField('Đơn vị / Đoàn kiểm tra:', _orgNameCtrl),
              Row(
                children: [
                  Expanded(child: _buildCompactField('Số văn bản:', _docNumCtrl)),
                  const SizedBox(width: 8),
                  Expanded(child: _buildCompactField('Địa danh & Ngày tháng:', _locDateCtrl)),
                ],
              ),
              _buildCompactField('Tiêu đề Biên bản:', _reportTitleCtrl),
              Row(
                children: [
                  Expanded(child: _buildCompactField('Bắt đầu lúc:', _startTimeCtrl)),
                  const SizedBox(width: 8),
                  Expanded(child: _buildCompactField('Kết thúc lúc:', _endTimeCtrl)),
                ],
              ),
              _buildCompactField('Đại diện Giám sát thi công (TVGS / CĐT):', _supervisorCtrl),
              _buildCompactField('Đại diện Kỹ thuật thi công (Nhà thầu):', _contractorCtrl),

              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _exportFieldInspectionDocx,
                  icon: const Icon(Icons.description),
                  label: const Text('Xuất Biên Bản Nghiệm Thu Hiện Trường (.DOCX)'),
                ),
              ),

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Divider(),
              ),
              Text(
                '1. DANH SÁCH BIÊN BẢN WORD (.DOCX) ĐÃ XUẤT (${_exportedDocx.length}):',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 6),
              if (_exportedDocx.isEmpty)
                const Text('Chưa có Biên bản .docx nào. Hãy nhấn nút Xuất Biên Bản ở trên.', style: TextStyle(fontSize: 12))
              else
                for (final doc in _exportedDocx)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.article_outlined, color: Colors.blue),
                    title: Text((doc['name'] ?? '').toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    subtitle: Text('${doc['modified']} • ${doc['sizeKB']} KB', style: const TextStyle(fontSize: 11.5)),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: 'Chia sẻ .docx',
                          icon: const Icon(Icons.share, size: 19),
                          onPressed: () => _shareFile(
                            (doc['path'] ?? '').toString(),
                            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                          ),
                        ),
                        IconButton(
                          tooltip: 'Xóa',
                          icon: const Icon(Icons.delete_outline, size: 19),
                          onPressed: () => _deleteFile((doc['path'] ?? '').toString()),
                        ),
                      ],
                    ),
                  ),

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(),
              ),
              Text(
                '2. KHO ẢNH HIỆN TRƯỜNG ĐÃ ĐÓNG DẤU KỸ THUẬT (${_stampedPhotos.length}):',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 6),
              for (final p in _stampedPhotos)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.verified_outlined, color: Colors.green),
                  title: Text((p['name'] ?? '').toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                  subtitle: Text(
                    '${p['modified']} • ${p['sizeKB']} KB • SHA-256: ${(p['sha256'] ?? '').toString().take(12).toUpperCase()}...',
                    style: const TextStyle(fontSize: 11.5),
                  ),
                  onTap: () {
                    setState(() => _selectedPhotoPath = (p['path'] ?? '').toString());
                    DefaultTabController.of(context).animateTo(0);
                  },
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.share, size: 19),
                        onPressed: () => _shareFile((p['path'] ?? '').toString(), 'image/jpeg'),
                      ),
                      IconButton(
                        icon: const Icon(Icons.delete_outline, size: 19),
                        onPressed: () => _deleteFile((p['path'] ?? '').toString()),
                      ),
                    ],
                  ),
                ),

              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Divider(),
              ),
              Text(
                '3. GHI ÂM THUYẾT MINH HIỆN TRƯỜNG WAV (${_recordings.length}):',
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 6),
              if (_recordings.isEmpty)
                const Text('Chưa có tệp ghi âm hiện trường .wav.', style: TextStyle(fontSize: 12))
              else
                for (final r in _recordings)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.audiotrack_outlined, color: Colors.amber),
                    title: Text((r['name'] ?? '').toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    subtitle: Text('${r['durationSec']}s • ${r['sizeKB']} KB • ${r['modified']}', style: const TextStyle(fontSize: 11.5)),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.play_arrow, size: 20),
                          onPressed: () => _channel.invokeMethod('playRecording', {'path': r['path']}),
                        ),
                        IconButton(
                          icon: const Icon(Icons.share, size: 19),
                          onPressed: () => _shareFile((r['path'] ?? '').toString(), 'audio/wav'),
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete_outline, size: 19),
                          onPressed: () => _deleteFile((r['path'] ?? '').toString()),
                        ),
                      ],
                    ),
                  ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildCompactField(String label, TextEditingController ctrl) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: ctrl,
        style: const TextStyle(fontSize: 13),
        decoration: InputDecoration(
          labelText: label,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),
    );
  }
}

extension _StrTake on String {
  String take(int n) => length <= n ? this : substring(0, n);
}
