class Graph:

    def __init__(self, num_vertices):
        self.num_vertices= num_vertices
        self.adj_list = [[] for _ in range(num_vertices)]

    def add_edge(self, u, v):
        self.adj_list[u].append(v)
        self.adj_list[v].append(u)

print("--- BẮT ĐẦU KIỂM TRA CLASS GRAPH ---")
de_bai_hinh_vuong = Graph(num_vertices=4)

de_bai_hinh_vuong.add_edge(0, 1)
de_bai_hinh_vuong.add_edge(1, 2)
de_bai_hinh_vuong.add_edge(2, 3)
de_bai_hinh_vuong.add_edge(3, 0)

print(f"Số đỉnh của 'đề bài' này là: {de_bai_hinh_vuong.num_vertices}")
print(f"Danh sách hàng xóm của các đỉnh: {de_bai_hinh_vuong.adj_list}")

print(f"Các hàng xóm của đỉnh số 1 là: {de_bai_hinh_vuong.adj_list[1]}")
print("--- KẾT THÚC KIỂM TRA ---")